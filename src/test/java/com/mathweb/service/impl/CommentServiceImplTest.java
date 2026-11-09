package com.mathweb.service.impl;

import com.mathweb.dto.request.CreateCommentRequest;
import com.mathweb.dto.request.UpdateCommentRequest;
import com.mathweb.dto.response.CommentResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.entity.Comment;
import com.mathweb.entity.CommentVote;
import com.mathweb.entity.User;
import com.mathweb.entity.Video;
import com.mathweb.enums.NotificationType;
import com.mathweb.enums.Role;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ForbiddenException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.mapper.CommentMapper;
import com.mathweb.repository.CommentRepository;
import com.mathweb.repository.CommentVoteRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.repository.VideoRepository;
import com.mathweb.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link CommentServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long VIDEO_ID = 5L;
    private static final Long COMMENT_ID = 10L;

    @Mock private CommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private CommentVoteRepository voteRepository;
    @Mock private CommentMapper commentMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private CommentServiceImpl service;

    private User user;
    private User other;
    private Video video;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxCommentDepth", 2);

        user = User.builder().firstName("Ada").lastName("L").email("a@example.com").role(Role.STUDENT).build();
        user.setId(USER_ID);
        other = User.builder().firstName("Bob").lastName("B").email("b@example.com").role(Role.STUDENT).build();
        other.setId(OTHER_ID);
        video = Video.builder().build();
        video.setId(VIDEO_ID);

        lenient().when(commentMapper.toResponse(any(Comment.class))).thenAnswer(i -> {
            Comment c = i.getArgument(0);
            return CommentResponse.builder().id(c.getId()).content(c.getContent()).build();
        });
    }

    private Comment comment(Long id, User author, Video v, int depth) {
        Comment c = Comment.builder().content("text").user(author).video(v).depth(depth).build();
        c.setId(id);
        return c;
    }

    private CreateCommentRequest createRequest(String content, Long parentId) {
        CreateCommentRequest r = new CreateCommentRequest();
        r.setVideoId(VIDEO_ID);
        r.setContent(content);
        r.setParentId(parentId);
        return r;
    }

    @Nested
    class Create {

        @Test
        void throwsNotFound_whenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createComment(createRequest("hi", null), USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsNotFound_whenVideoMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createComment(createRequest("hi", null), USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(commentRepository, never()).save(any());
        }

        @Test
        void topLevelComment_isSanitized_depthZero_andSendsNoNotification() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));

            service.createComment(createRequest("  hi\u0000 there  ", null), USER_ID);

            ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
            verify(commentRepository).save(saved.capture());
            assertThat(saved.getValue().getContent()).isEqualTo("hi there");
            assertThat(saved.getValue().getDepth()).isZero();
            assertThat(saved.getValue().getParent()).isNull();
            verify(notificationService, never()).notify(any(), any(), any(), any(), any());
        }

        @Test
        void reply_incrementsDepth_andNotifiesParentAuthor() {
            Comment parent = comment(COMMENT_ID, other, video, 0);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(parent));

            service.createComment(createRequest("agree", COMMENT_ID), USER_ID);

            ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
            verify(commentRepository).save(saved.capture());
            assertThat(saved.getValue().getParent()).isSameAs(parent);
            assertThat(saved.getValue().getDepth()).isEqualTo(1);
            verify(notificationService).notify(eq(OTHER_ID), eq(NotificationType.COMMENT_REPLY),
                    eq("Ada replied to your comment"), eq("agree"), eq("/videos/" + VIDEO_ID));
        }

        @Test
        void reply_truncatesLongNotificationText() {
            Comment parent = comment(COMMENT_ID, other, video, 0);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(parent));

            service.createComment(createRequest("x".repeat(200), COMMENT_ID), USER_ID);

            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notify(any(), any(), any(), text.capture(), any());
            assertThat(text.getValue()).hasSize(121).endsWith("…");
        }

        @Test
        void reply_toOwnComment_doesNotNotify() {
            Comment parent = comment(COMMENT_ID, user, video, 0);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(parent));

            service.createComment(createRequest("me again", COMMENT_ID), USER_ID);

            verify(notificationService, never()).notify(any(), any(), any(), any(), any());
        }

        @Test
        void reply_throwsNotFound_whenParentMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createComment(createRequest("hi", COMMENT_ID), USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void reply_rejected_whenParentBelongsToDifferentVideo() {
            Video otherVideo = Video.builder().build();
            otherVideo.setId(99L);
            Comment parent = comment(COMMENT_ID, other, otherVideo, 0);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(parent));

            assertThatThrownBy(() -> service.createComment(createRequest("hi", COMMENT_ID), USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Parent comment belongs to a different video");
            verify(commentRepository, never()).save(any());
        }

        @Test
        void reply_rejected_whenMaxDepthReached() {
            Comment parent = comment(COMMENT_ID, other, video, 2);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(videoRepository.findById(VIDEO_ID)).thenReturn(Optional.of(video));
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(parent));

            assertThatThrownBy(() -> service.createComment(createRequest("hi", COMMENT_ID), USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Maximum comment nesting depth reached");
        }
    }

    @Nested
    class Update {

        private UpdateCommentRequest request(String content) {
            UpdateCommentRequest r = new UpdateCommentRequest();
            r.setContent(content);
            return r;
        }

        @Test
        void throwsNotFound_whenMissing() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateComment(COMMENT_ID, request("x"), USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsForbidden_whenNotOwner() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment(COMMENT_ID, other, video, 0)));

            assertThatThrownBy(() -> service.updateComment(COMMENT_ID, request("x"), USER_ID))
                    .isInstanceOf(ForbiddenException.class);
            verify(commentRepository, never()).save(any());
        }

        @Test
        void throwsBadRequest_whenDeleted() {
            Comment c = comment(COMMENT_ID, user, video, 0);
            c.setDeleted(true);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.updateComment(COMMENT_ID, request("x"), USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cannot edit a deleted comment");
        }

        @Test
        void updatesContent_marksEdited_andReturnsResponse() {
            Comment c = comment(COMMENT_ID, user, video, 0);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(c));

            CommentResponse response = service.updateComment(COMMENT_ID, request("  new text  "), USER_ID);

            assertThat(c.getContent()).isEqualTo("new text");
            assertThat(c.getEdited()).isTrue();
            verify(commentRepository).save(c);
            assertThat(response.getId()).isEqualTo(COMMENT_ID);
            assertThat(response.getScore()).isZero();
            assertThat(response.getMyVote()).isZero();
        }
    }

    @Nested
    class Delete {

        @Test
        void throwsNotFound_whenCommentMissing() {
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteComment(COMMENT_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void owner_softDeletesComment() {
            Comment c = comment(COMMENT_ID, user, video, 0);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(c));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            service.deleteComment(COMMENT_ID, USER_ID);

            assertThat(c.getDeleted()).isTrue();
            assertThat(c.getContent()).isEqualTo("[deleted]");
            verify(commentRepository).save(c);
        }

        @Test
        void admin_canDeleteSomeoneElsesComment() {
            User admin = User.builder().firstName("Root").lastName("A").role(Role.ADMIN).build();
            admin.setId(77L);
            Comment c = comment(COMMENT_ID, other, video, 0);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(c));
            when(userRepository.findById(77L)).thenReturn(Optional.of(admin));

            service.deleteComment(COMMENT_ID, 77L);

            assertThat(c.getDeleted()).isTrue();
        }

        @Test
        void nonOwnerStudent_isForbidden() {
            Comment c = comment(COMMENT_ID, other, video, 0);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(c));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.deleteComment(COMMENT_ID, USER_ID))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(c.getDeleted()).isFalse();
            verify(commentRepository, never()).save(any());
        }
    }

    @Nested
    class Voting {

        private Comment votable;

        @BeforeEach
        void init() {
            votable = comment(COMMENT_ID, other, video, 0);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(votable));
        }

        private CommentVote vote(int value) {
            return CommentVote.builder().comment(votable).user(user).value(value).build();
        }

        @Test
        void throwsBadRequest_whenCommentDeleted() {
            votable.setDeleted(true);

            assertThatThrownBy(() -> service.upvoteComment(COMMENT_ID, USER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cannot vote on a deleted comment");
        }

        @Test
        void firstUpvote_savesVote_andIncrementsUpvotes() {
            when(voteRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            service.upvoteComment(COMMENT_ID, USER_ID);

            ArgumentCaptor<CommentVote> saved = ArgumentCaptor.forClass(CommentVote.class);
            verify(voteRepository).save(saved.capture());
            assertThat(saved.getValue().getValue()).isEqualTo(1);
            verify(commentRepository).adjustVotes(COMMENT_ID, 1, 0);
        }

        @Test
        void firstDownvote_savesVote_andIncrementsDownvotes() {
            when(voteRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.empty());
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            service.downvoteComment(COMMENT_ID, USER_ID);

            verify(commentRepository).adjustVotes(COMMENT_ID, 0, 1);
        }

        @Test
        void sameButtonAgain_removesTheVote() {
            CommentVote existing = vote(1);
            when(voteRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.of(existing));

            service.upvoteComment(COMMENT_ID, USER_ID);

            verify(voteRepository).delete(existing);
            verify(commentRepository).adjustVotes(COMMENT_ID, -1, 0);
        }

        @Test
        void downvoteAgain_removesTheDownvote() {
            CommentVote existing = vote(-1);
            when(voteRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.of(existing));

            service.downvoteComment(COMMENT_ID, USER_ID);

            verify(voteRepository).delete(existing);
            verify(commentRepository).adjustVotes(COMMENT_ID, 0, -1);
        }

        @Test
        void oppositeButton_switchesUpToDown() {
            CommentVote existing = vote(1);
            when(voteRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.of(existing));

            service.downvoteComment(COMMENT_ID, USER_ID);

            assertThat(existing.getValue()).isEqualTo(-1);
            verify(voteRepository).save(existing);
            verify(commentRepository).adjustVotes(COMMENT_ID, -1, 1);
        }

        @Test
        void oppositeButton_switchesDownToUp() {
            CommentVote existing = vote(-1);
            when(voteRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.of(existing));

            service.upvoteComment(COMMENT_ID, USER_ID);

            assertThat(existing.getValue()).isEqualTo(1);
            verify(commentRepository).adjustVotes(COMMENT_ID, 1, -1);
        }
    }

    @Nested
    class GetVideoComments {

        @Test
        void mapsScoreMyVoteAndNestedReplies() {
            Comment top = comment(COMMENT_ID, other, video, 0);
            top.setUpvotes(5);
            top.setDownvotes(2);
            Comment reply = comment(11L, user, video, 1);
            reply.setParent(top);
            CommentVote myVote = CommentVote.builder().comment(top).user(user).value(1).build();

            when(commentRepository.findTopLevelByVideoId(eq(VIDEO_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(top), PageRequest.of(0, 10), 1));
            when(commentRepository.findRepliesByVideoId(VIDEO_ID)).thenReturn(List.of(reply));
            when(voteRepository.findByUserIdAndVideoId(USER_ID, VIDEO_ID)).thenReturn(List.of(myVote));

            PageResponse<CommentResponse> page = service.getVideoComments(VIDEO_ID, 0, 10, USER_ID);

            assertThat(page.getTotalElements()).isEqualTo(1);
            CommentResponse r = page.getContent().get(0);
            assertThat(r.getScore()).isEqualTo(3);
            assertThat(r.getMyVote()).isEqualTo(1);
            assertThat(r.getReplies()).hasSize(1);
            assertThat(r.getReplies().get(0).getId()).isEqualTo(11L);
            assertThat(r.getReplies().get(0).getMyVote()).isZero();
        }

        @Test
        void anonymousViewer_getsNoVotes_andDoesNotQueryVotes() {
            Comment top = comment(COMMENT_ID, other, video, 0);
            when(commentRepository.findTopLevelByVideoId(eq(VIDEO_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(top), PageRequest.of(0, 10), 1));
            when(commentRepository.findRepliesByVideoId(VIDEO_ID)).thenReturn(List.of());

            PageResponse<CommentResponse> page = service.getVideoComments(VIDEO_ID, 0, 10, null);

            assertThat(page.getContent().get(0).getMyVote()).isZero();
            verify(voteRepository, never()).findByUserIdAndVideoId(anyLong(), anyLong());
        }
    }
}