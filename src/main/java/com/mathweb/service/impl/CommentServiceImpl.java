package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateCommentRequest;
import com.mathweb.dto.request.UpdateCommentRequest;
import com.mathweb.dto.response.CommentResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.entity.Comment;
import com.mathweb.entity.CommentVote;
import com.mathweb.entity.User;
import com.mathweb.entity.Video;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ForbiddenException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.CommentVoteRepository;
import com.mathweb.util.SanitizationUtil;
import com.mathweb.mapper.CommentMapper;
import com.mathweb.repository.CommentRepository;
import com.mathweb.repository.CommentVoteRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.repository.VideoRepository;
import com.mathweb.service.CommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final CommentVoteRepository voteRepository;
    private final CommentMapper commentMapper;

    @Value("${app.max-comment-depth}")
    private int maxCommentDepth;

    public CommentServiceImpl(CommentRepository commentRepository,
                              UserRepository userRepository,
                              VideoRepository videoRepository, CommentVoteRepository commentVoteRepository,
                              CommentMapper commentMapper) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
        this.voteRepository = commentVoteRepository;
        this.commentMapper = commentMapper;
    }

    @Override
    @Transactional
    public CommentResponse createComment(CreateCommentRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new ResourceNotFoundException("Video", request.getVideoId()));

        Comment comment = Comment.builder()
                .content(SanitizationUtil.sanitizeComment(request.getContent()))
                .user(user)
                .video(video)
                .depth(0)
                .build();

        // Handle reply
        if (request.getParentId() != null) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", request.getParentId()));

            if (!parent.getVideo().getId().equals(video.getId())) {
                throw new BadRequestException("Parent comment belongs to a different video");
            }

            if (parent.getDepth() >= maxCommentDepth) {
                throw new BadRequestException("Maximum comment nesting depth reached");
            }

            comment.setParent(parent);
            comment.setDepth(parent.getDepth() + 1);
        }

        commentRepository.save(comment);
        return mapToCommentResponse(comment, Collections.emptyMap(), Collections.emptyMap());
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId,
                                         UpdateCommentRequest request,
                                         Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own comments");
        }

        if (comment.getDeleted()) {
            throw new BadRequestException("Cannot edit a deleted comment");
        }

        comment.setContent(SanitizationUtil.sanitizeComment(request.getContent()));
        comment.setEdited(true);
        commentRepository.save(comment);

        return buildResponse(comment.getId(), userId);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        boolean isOwner = comment.getUser().getId().equals(userId);
        boolean isAdmin = user.getRole().name().equals("ADMIN");

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You can only delete your own comments");
        }

        // Soft delete — keep structure for replies
        comment.setDeleted(true);
        comment.setContent("[deleted]");
        commentRepository.save(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getVideoComments(Long videoId, int page, int size,
                                                          Long currentUserId) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Page<Comment> commentPage = commentRepository.findTopLevelByVideoId(videoId, pageRequest);
        Map<Long, List<Comment>> repliesByParent = repliesByParent(videoId);
        Map<Long, Integer> myVotes = myVotes(videoId, currentUserId);

        return PageResponse.<CommentResponse>builder()
                .content(commentPage.getContent().stream()
                        .map(c -> mapToCommentResponse(c, repliesByParent, myVotes)).toList())
                .page(commentPage.getNumber())
                .size(commentPage.getSize())
                .totalElements(commentPage.getTotalElements())
                .totalPages(commentPage.getTotalPages())
                .first(commentPage.isFirst())
                .last(commentPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public CommentResponse upvoteComment(Long commentId, Long userId) {
        return toggleVote(commentId, userId, 1);
    }

    @Override
    @Transactional
    public CommentResponse downvoteComment(Long commentId, Long userId) {
        return toggleVote(commentId, userId, -1);
    }

    private CommentResponse toggleVote(Long commentId, Long userId, int value) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (comment.getDeleted()) {
            throw new BadRequestException("Cannot vote on a deleted comment");
        }

        Optional<CommentVote> existing = voteRepository.findByCommentIdAndUserId(commentId, userId);

        if (existing.isEmpty()) {
            // no vote yet -> add
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));
            voteRepository.save(CommentVote.builder()
                    .comment(comment).user(user).value(value).build());
            commentRepository.adjustVotes(commentId, value == 1 ? 1 : 0, value == -1 ? 1 : 0);

        } else if (existing.get().getValue() == value) {
            // same button again -> take the vote back
            voteRepository.delete(existing.get());
            commentRepository.adjustVotes(commentId, value == 1 ? -1 : 0, value == -1 ? -1 : 0);

        } else {
            // opposite button -> switch
            CommentVote vote = existing.get();
            vote.setValue(value);
            voteRepository.save(vote);
            commentRepository.adjustVotes(commentId, value == 1 ? 1 : -1, value == 1 ? -1 : 1);
        }

        return buildResponse(commentId, userId);
    }

    // ===== PRIVATE HELPERS =====

    private CommentResponse mapToCommentResponse(Comment comment) {
        List<Comment> replies = commentRepository
                .findByParentIdOrderByCreatedAtAsc(comment.getId());

        CommentResponse response = commentMapper.toResponse(comment);
        response.setReplies(replies.stream()
                .map(this::mapToCommentResponse)
                .toList());
        return response;
    }

    private Map<Long, List<Comment>> repliesByParent(Long videoId) {
        Map<Long, List<Comment>> map = new HashMap<>();
        for (Comment reply : commentRepository.findRepliesByVideoId(videoId)) {
            map.computeIfAbsent(reply.getParent().getId(), k -> new ArrayList<>()).add(reply);
        }
        return map;
    }

    private Map<Long, Integer> myVotes(Long videoId, Long userId) {
        if (userId == null) return Collections.emptyMap();
        Map<Long, Integer> map = new HashMap<>();
        for (CommentVote v : voteRepository.findByUserIdAndVideoId(userId, videoId)) {
            map.put(v.getComment().getId(), v.getValue());
        }
        return map;
    }

    private CommentResponse buildResponse(Long commentId, Long userId) {
        Comment fresh = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
        Long videoId = fresh.getVideo().getId();
        return mapToCommentResponse(fresh, repliesByParent(videoId), myVotes(videoId, userId));
    }

    private CommentResponse mapToCommentResponse(Comment comment,
                                                 Map<Long, List<Comment>> repliesByParent,
                                                 Map<Long, Integer> myVotes) {
        CommentResponse response = commentMapper.toResponse(comment);
        int up = comment.getUpvotes() != null ? comment.getUpvotes() : 0;
        int down = comment.getDownvotes() != null ? comment.getDownvotes() : 0;
        response.setScore(up - down);
        response.setMyVote(myVotes.getOrDefault(comment.getId(), 0));
        response.setReplies(repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                .map(r -> mapToCommentResponse(r, repliesByParent, myVotes))
                .toList());
        return response;
    }
}