package com.mathweb.service.impl;

import com.mathweb.dto.request.GrantAccessRequest;
import com.mathweb.dto.response.AccessGrantResponse;
import com.mathweb.dto.response.AdminUserResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.entity.AccessGrant;
import com.mathweb.entity.Subscription;
import com.mathweb.entity.User;
import com.mathweb.enums.AccessGrantAction;
import com.mathweb.enums.Role;
import com.mathweb.enums.SubscriptionStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.service.FileStorageService;
import com.mathweb.repository.AccessGrantRepository;
import com.mathweb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/** Unit tests for {@link AdminUserService}. */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private AccessGrantRepository accessGrantRepository;
    @Mock private AccessGrantService accessGrantService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private AdminUserService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().firstName("Ada").lastName("Lovelace").email("ada@example.com")
                .role(Role.STUDENT).build();
        user.setId(USER_ID);
        lenient().when(fileStorageService.getFileUrl(any()))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private Subscription activeSub() {
        return Subscription.builder().user(user).status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(10)).build();
    }

    private GrantAccessRequest request(int days, String note) {
        GrantAccessRequest r = new GrantAccessRequest();
        r.setDays(days);
        r.setNote(note);
        return r;
    }

    @Nested
    class ListUsers {

        @Test
        void clampsPageAndSize_andTrimsKeyword() {
            when(userRepository.searchForAdmin(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.listUsers(-1, 500, "  ada  ", Role.STUDENT, true, false);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).searchForAdmin(eq("ada"), eq(Role.STUDENT), eq(true), eq(false),
                    any(LocalDateTime.class), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isZero();
            assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        void nullKeywordBecomesEmpty_andLongKeywordIsTruncated() {
            when(userRepository.searchForAdmin(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.listUsers(0, 20, null, null, null, null);
            service.listUsers(0, 20, "x".repeat(300), null, null, null);

            ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
            verify(userRepository, org.mockito.Mockito.times(2)).searchForAdmin(
                    keyword.capture(), any(), any(), any(), any(), any(Pageable.class));
            assertThat(keyword.getAllValues().get(0)).isEmpty();
            assertThat(keyword.getAllValues().get(1)).hasSize(100);
        }

        @Test
        void mapsUserWithAndWithoutSubscription() {
            User subscribed = User.builder().firstName("Bob").lastName("B").email("b@example.com")
                    .role(Role.STUDENT).build();
            subscribed.setId(2L);
            subscribed.setSubscription(activeSub());
            when(userRepository.searchForAdmin(any(), any(), any(), any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(user, subscribed)));

            PageResponse<AdminUserResponse> page = service.listUsers(0, 20, null, null, null, null);

            assertThat(page.getContent()).hasSize(2);
            AdminUserResponse plain = page.getContent().get(0);
            assertThat(plain.getEmail()).isEqualTo("ada@example.com");
            assertThat(plain.isHasActiveAccess()).isFalse();
            assertThat(plain.getSubscriptionStatus()).isNull();
            assertThat(plain.getAccessEndsAt()).isNull();

            AdminUserResponse withAccess = page.getContent().get(1);
            assertThat(withAccess.isHasActiveAccess()).isTrue();
            assertThat(withAccess.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(withAccess.getAccessEndsAt()).isNotNull();
        }
    }

    @Nested
    class GetHistory {

        @Test
        void throwsNotFound_whenUserMissing() {
            when(userRepository.existsById(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.getHistory(USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(accessGrantRepository, never()).findByUserIdOrderByCreatedAtDescIdDesc(anyLong(), any());
        }

        @Test
        void resolvesAdminNames_andLeavesUnknownOrNullAdminsEmpty() {
            User admin = User.builder().firstName("Root").lastName("Admin").build();
            admin.setId(ADMIN_ID);
            AccessGrant byAdmin = AccessGrant.builder().userId(USER_ID).grantedBy(ADMIN_ID)
                    .action(AccessGrantAction.GRANT).days(7).note("gift").build();
            AccessGrant bySystem = AccessGrant.builder().userId(USER_ID).grantedBy(null)
                    .action(AccessGrantAction.REVOKE).build();
            when(userRepository.existsById(USER_ID)).thenReturn(true);
            when(accessGrantRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any(Pageable.class)))
                    .thenReturn(List.of(byAdmin, bySystem));
            when(userRepository.findAllById(Set.of(ADMIN_ID))).thenReturn(List.of(admin));

            List<AccessGrantResponse> history = service.getHistory(USER_ID);

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getGrantedByName()).isEqualTo("Root Admin");
            assertThat(history.get(0).getDays()).isEqualTo(7);
            assertThat(history.get(0).getNote()).isEqualTo("gift");
            assertThat(history.get(1).getGrantedById()).isNull();
            assertThat(history.get(1).getGrantedByName()).isNull();
        }
    }

    @Nested
    class GrantAccess {

        @Test
        void savesAuditRow_andReturnsActiveResponse() {
            Subscription sub = activeSub();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(accessGrantService.grantFreeDays(USER_ID, 14)).thenReturn(sub);

            AdminUserResponse response = service.grantAccess(ADMIN_ID, USER_ID, request(14, "  welcome  "));

            ArgumentCaptor<AccessGrant> audit = ArgumentCaptor.forClass(AccessGrant.class);
            verify(accessGrantRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(AccessGrantAction.GRANT);
            assertThat(audit.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(audit.getValue().getGrantedBy()).isEqualTo(ADMIN_ID);
            assertThat(audit.getValue().getDays()).isEqualTo(14);
            assertThat(audit.getValue().getNote()).isEqualTo("welcome");
            assertThat(audit.getValue().getPeriodEndAfter()).isEqualTo(sub.getCurrentPeriodEnd());
            assertThat(response.isHasActiveAccess()).isTrue();
        }

        @Test
        void blankNoteIsStoredAsNull_andLongNoteIsTruncated() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(accessGrantService.grantFreeDays(eq(USER_ID), any(Integer.class))).thenReturn(activeSub());

            service.grantAccess(ADMIN_ID, USER_ID, request(1, "   "));
            service.grantAccess(ADMIN_ID, USER_ID, request(1, "n".repeat(900)));

            ArgumentCaptor<AccessGrant> audit = ArgumentCaptor.forClass(AccessGrant.class);
            verify(accessGrantRepository, org.mockito.Mockito.times(2)).save(audit.capture());
            assertThat(audit.getAllValues().get(0).getNote()).isNull();
            assertThat(audit.getAllValues().get(1).getNote()).hasSize(500);
        }

        @Test
        void throwsNotFound_andDoesNotGrant_whenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.grantAccess(ADMIN_ID, USER_ID, request(7, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(accessGrantService, never()).grantFreeDays(anyLong(), org.mockito.ArgumentMatchers.anyInt());
            verify(accessGrantRepository, never()).save(any());
        }
    }

    @Nested
    class RevokeAccess {

        @Test
        void savesRevokeAuditRow_withoutDays() {
            Subscription cancelled = Subscription.builder().user(user).status(SubscriptionStatus.CANCELLED)
                    .currentPeriodEnd(LocalDateTime.now()).build();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(accessGrantService.revoke(USER_ID)).thenReturn(cancelled);

            AdminUserResponse response = service.revokeAccess(ADMIN_ID, USER_ID, "abuse");

            ArgumentCaptor<AccessGrant> audit = ArgumentCaptor.forClass(AccessGrant.class);
            verify(accessGrantRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(AccessGrantAction.REVOKE);
            assertThat(audit.getValue().getDays()).isNull();
            assertThat(audit.getValue().getNote()).isEqualTo("abuse");
            assertThat(response.isHasActiveAccess()).isFalse();
            assertThat(response.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        }

        @Test
        void propagatesBadRequest_andWritesNoAuditRow() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(accessGrantService.revoke(USER_ID))
                    .thenThrow(new BadRequestException("This user has no active access"));

            assertThatThrownBy(() -> service.revokeAccess(ADMIN_ID, USER_ID, null))
                    .isInstanceOf(BadRequestException.class);
            verify(accessGrantRepository, never()).save(any());
        }

        @Test
        void throwsNotFound_whenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeAccess(ADMIN_ID, USER_ID, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}