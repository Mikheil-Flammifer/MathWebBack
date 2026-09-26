package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateCommentRequest;
import com.mathweb.dto.request.UpdateCommentRequest;
import com.mathweb.dto.response.CommentResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.entity.Comment;
import com.mathweb.entity.User;
import com.mathweb.entity.Video;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ForbiddenException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.CommentRepository;
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

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    @Value("${app.max-comment-depth}")
    private int maxCommentDepth;

    public CommentServiceImpl(CommentRepository commentRepository,
                              UserRepository userRepository,
                              VideoRepository videoRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
    }

    @Override
    @Transactional
    public CommentResponse createComment(CreateCommentRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new ResourceNotFoundException("Video", request.getVideoId()));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .user(user)
                .video(video)
                .depth(0)
                .build();

        // Handle reply
        if (request.getParentId() != null) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", request.getParentId()));

            if (parent.getDepth() >= maxCommentDepth) {
                throw new BadRequestException("Maximum comment nesting depth reached");
            }

            comment.setParent(parent);
            comment.setDepth(parent.getDepth() + 1);
        }

        commentRepository.save(comment);
        log.info("Comment created by user {} on video {}", userId, request.getVideoId());

        return mapToCommentResponse(comment);
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

        comment.setContent(request.getContent());
        comment.setEdited(true);
        commentRepository.save(comment);

        return mapToCommentResponse(comment);
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

        log.info("Comment {} soft-deleted by user {}", commentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getVideoComments(Long videoId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Page<Comment> commentPage = commentRepository
                .findTopLevelByVideoId(videoId, pageRequest);

        return PageResponse.<CommentResponse>builder()
                .content(commentPage.getContent().stream()
                        .map(this::mapToCommentResponse).toList())
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
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        comment.setUpvotes(comment.getUpvotes() + 1);
        commentRepository.save(comment);

        return mapToCommentResponse(comment);
    }

    // ===== PRIVATE HELPERS =====

    private CommentResponse mapToCommentResponse(Comment comment) {
        List<Comment> replies = commentRepository
                .findByParentIdOrderByCreatedAtAsc(comment.getId());

        UserResponse userResponse = UserResponse.builder()
                .id(comment.getUser().getId())
                .firstName(comment.getUser().getFirstName())
                .lastName(comment.getUser().getLastName())
                .avatarUrl(comment.getUser().getAvatarUrl())
                .build();

        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getDisplayContent())
                .depth(comment.getDepth())
                .edited(comment.getEdited())
                .deleted(comment.getDeleted())
                .upvotes(comment.getUpvotes())
                .user(userResponse)
                .videoId(comment.getVideo().getId())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .replies(replies.stream().map(this::mapToCommentResponse).toList())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}