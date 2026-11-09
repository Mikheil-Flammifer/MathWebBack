package com.mathweb.service.impl;

import com.mathweb.dto.response.NotificationResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.entity.Notification;
import com.mathweb.entity.User;
import com.mathweb.enums.NotificationType;
import com.mathweb.enums.Role;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.NotificationRepository;
import com.mathweb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link NotificationServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private NotificationServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("student@example.com").build();
        user.setId(1L);
    }

    private Notification notification(boolean read) {
        Notification n = Notification.builder()
                .user(user).type(NotificationType.SYSTEM)
                .title("Hello").message("World").link("/x").isRead(read).build();
        n.setId(10L);
        return n;
    }

    @Nested
    class Create {

        @Test
        void notify_savesNotificationWithoutDedupeKey() {
            when(userRepository.getReferenceById(1L)).thenReturn(user);

            service.notify(1L, NotificationType.SYSTEM, "Title", "Body", "/link");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getType()).isEqualTo(NotificationType.SYSTEM);
            assertThat(saved.getTitle()).isEqualTo("Title");
            assertThat(saved.getMessage()).isEqualTo("Body");
            assertThat(saved.getLink()).isEqualTo("/link");
            assertThat(saved.getDedupeKey()).isNull();
            assertThat(saved.getIsRead()).isFalse();
        }

        @Test
        void notifyOnce_skips_whenKeyAlreadyExists() {
            when(notificationRepository.existsByUserIdAndDedupeKey(1L, "k")).thenReturn(true);

            service.notifyOnce(1L, NotificationType.SYSTEM, "T", "M", null, "k");

            verify(notificationRepository, never()).save(any());
        }

        @Test
        void notifyOnce_saves_withDedupeKey_whenNew() {
            when(notificationRepository.existsByUserIdAndDedupeKey(1L, "k")).thenReturn(false);
            when(userRepository.getReferenceById(1L)).thenReturn(user);

            service.notifyOnce(1L, NotificationType.SYSTEM, "T", "M", null, "k");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getDedupeKey()).isEqualTo("k");
        }

        @Test
        @SuppressWarnings("unchecked")
        void notifyAllStudents_savesOneNotificationPerActiveStudent() {
            User other = User.builder().email("b@example.com").build();
            other.setId(2L);
            when(userRepository.findActiveVerifiedIdsByRole(Role.STUDENT)).thenReturn(List.of(1L, 2L));
            when(userRepository.getReferenceById(1L)).thenReturn(user);
            when(userRepository.getReferenceById(2L)).thenReturn(other);

            service.notifyAllStudents(NotificationType.NEW_VIDEO, "New", "Video", "/v");

            ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(2)
                    .extracting(Notification::getUser).containsExactly(user, other);
            assertThat(captor.getValue()).allMatch(n -> n.getType() == NotificationType.NEW_VIDEO);
        }
    }

    @Nested
    class Read {

        @Test
        void list_usesAllNotifications_andMapsToResponses() {
            Page<Notification> page = new PageImpl<>(List.of(notification(false)));
            when(notificationRepository.findByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

            PageResponse<NotificationResponse> result = service.list(1L, false, 0, 20);

            assertThat(result.getContent()).hasSize(1);
            NotificationResponse r = result.getContent().get(0);
            assertThat(r.getId()).isEqualTo(10L);
            assertThat(r.getTitle()).isEqualTo("Hello");
            assertThat(r.isRead()).isFalse();
            verify(notificationRepository, never()).findByUserIdAndIsReadFalse(any(), any());
        }

        @Test
        void list_usesUnreadQuery_whenUnreadOnly() {
            when(notificationRepository.findByUserIdAndIsReadFalse(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.list(1L, true, 0, 20);

            verify(notificationRepository, never()).findByUserId(any(), any());
        }

        @Test
        void list_clampsPageAndSize() {
            when(notificationRepository.findByUserId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.list(1L, false, -3, 1000);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(notificationRepository).findByUserId(eq(1L), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        void list_clampsSizeToAtLeastOne() {
            when(notificationRepository.findByUserId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.list(1L, false, 0, 0);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(notificationRepository).findByUserId(eq(1L), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        }

        @Test
        void unreadCount_delegatesToRepository() {
            when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(4L);

            assertThat(service.unreadCount(1L)).isEqualTo(4L);
        }
    }

    @Nested
    class Update {

        @Test
        void markRead_marksUnreadNotification_andSetsReadAt() {
            Notification n = notification(false);
            when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(n));

            NotificationResponse r = service.markRead(10L, 1L);

            assertThat(n.getIsRead()).isTrue();
            assertThat(n.getReadAt()).isNotNull();
            assertThat(r.isRead()).isTrue();
            verify(notificationRepository).save(n);
        }

        @Test
        void markRead_isNoOp_whenAlreadyRead() {
            Notification n = notification(true);
            LocalDateTime readAt = LocalDateTime.now().minusDays(1);
            n.setReadAt(readAt);
            when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(n));

            service.markRead(10L, 1L);

            assertThat(n.getReadAt()).isEqualTo(readAt);
            verify(notificationRepository, never()).save(any());
        }

        @Test
        void markRead_throwsNotFound_forOtherUsersNotification() {
            when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markRead(10L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void markAllRead_returnsNumberOfUpdatedRows() {
            when(notificationRepository.markAllRead(eq(1L), any(LocalDateTime.class))).thenReturn(5);

            assertThat(service.markAllRead(1L)).isEqualTo(5);
        }
    }

    @Nested
    class Delete {

        @Test
        void delete_removesOwnNotification() {
            Notification n = notification(false);
            when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(n));

            service.delete(10L, 1L);

            verify(notificationRepository).delete(n);
        }

        @Test
        void delete_throwsNotFound_whenMissing() {
            when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(10L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(notificationRepository, never()).delete(any());
        }

        @Test
        void purgeOlderThan_usesCutoffOfGivenDays() {
            when(notificationRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(7);

            int deleted = service.purgeOlderThan(30);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(notificationRepository).deleteOlderThan(captor.capture());
            assertThat(captor.getValue())
                    .isBetween(LocalDateTime.now().minusDays(30).minusMinutes(1),
                            LocalDateTime.now().minusDays(30).plusMinutes(1));
            assertThat(deleted).isEqualTo(7);
        }
    }
}