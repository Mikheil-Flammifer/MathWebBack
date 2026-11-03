
package com.mathweb.service.impl;

import com.mathweb.dto.request.ChangePasswordRequest;
import com.mathweb.dto.request.UpdateProfileRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.dto.response.UserStatsResponse;
import com.mathweb.entity.*;
import com.mathweb.enums.QuestStatus;
import com.mathweb.enums.Role;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.mapper.UserMapper;
import com.mathweb.repository.*;
import com.mathweb.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProblemAttemptRepository problemAttemptRepository;

    @Mock
    private UserQuestProgressRepository questProgressRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .passwordHash("encoded-password")
                .role(Role.STUDENT)
                .emailVerified(true)
                .active(true)
                .build();
        user.setId(1L);

        userResponse = UserResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .role(Role.STUDENT)
                .emailVerified(true)
                .active(true)
                .build();
    }

    // --------------------------------------------------
    // getUserById
    // --------------------------------------------------

    @Test
    void getUserById_returnsUserWhenFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = userService.getUserById(1L);

        assertThat(result).isEqualTo(userResponse);
        verify(userRepository).findById(1L);
        verify(userMapper).toResponse(user);
    }

    @Test
    void getUserById_throwsWhenUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userMapper, never()).toResponse(any());
    }

    // --------------------------------------------------
    // getCurrentUser
    // --------------------------------------------------

    @Test
    void getCurrentUser_returnsCurrentUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = userService.getCurrentUser(1L);

        assertThat(result).isEqualTo(userResponse);
        verify(userRepository).findById(1L);
    }

    // --------------------------------------------------
    // getAllUsers
    // --------------------------------------------------

    @Test
    void getAllUsers_returnsPaginatedUsers() {
        User secondUser = User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .passwordHash("encoded")
                .role(Role.STUDENT)
                .build();

        UserResponse secondResponse = UserResponse.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .role(Role.STUDENT)
                .build();

        Page<User> page = new PageImpl<>(
                List.of(user, secondUser),
                PageRequest.of(0, 2, Sort.by("createdAt").descending()),
                5
        );

        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponse(user)).thenReturn(userResponse);
        when(userMapper.toResponse(secondUser)).thenReturn(secondResponse);

        PageResponse<UserResponse> result = userService.getAllUsers(0, 2);

        assertThat(result.getContent()).containsExactly(userResponse, secondResponse);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();

        verify(userRepository).findAll(argThat((Pageable pageable) ->
                pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 2
                        && pageable.getSort().getOrderFor("createdAt") != null
                        && pageable.getSort().getOrderFor("createdAt").isDescending()
        ));
    }

    // --------------------------------------------------
    // updateUserRole
    // --------------------------------------------------

    @Test
    void updateUserRole_updatesRoleSuccessfully() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        UserResponse result = userService.updateUserRole(1L, 2L, Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result).isEqualTo(userResponse);

        verify(userRepository).save(user);
    }

    @Test
    void updateUserRole_rejectsChangingOwnRole() {
        assertThatThrownBy(() ->
                userService.updateUserRole(1L, 1L, Role.ADMIN))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot change your own role");

        verifyNoInteractions(userRepository);
    }

    @Test
    void updateUserRole_rejectsRemovingLastActiveAdmin() {
        user.setRole(Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() ->
                userService.updateUserRole(1L, 2L, Role.STUDENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot remove the last active admin");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserRole_allowsRemovingAdminWhenAnotherAdminExists() {
        user.setRole(Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(2L);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        userService.updateUserRole(1L, 2L, Role.STUDENT);

        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        verify(userRepository).save(user);
    }

    // --------------------------------------------------
    // updateProfile
    // --------------------------------------------------

    @Test
    void updateProfile_updatesProvidedNames() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        userService.updateProfile(1L, request);

        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Smith");

        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_preservesNamesThatWereNotProvided() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Jane");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        userService.updateProfile(1L, request);

        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Doe");
    }

    // --------------------------------------------------
    // updateAvatar
    // --------------------------------------------------

    @Test
    void updateAvatar_storesValidImage() {
        byte[] pngSignature = new byte[]{
                (byte) 0x89, 'P', 'N', 'G',
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };

        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                pngSignature
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileStorageService.storeImage(avatar)).thenReturn("images/new-avatar.png");
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        userService.updateAvatar(1L, avatar);

        assertThat(user.getAvatarUrl()).isEqualTo("images/new-avatar.png");
        verify(fileStorageService).storeImage(avatar);
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatar_deletesOldAvatarBeforeStoringNewOne() {
        user.setAvatarUrl("images/old-avatar.png");

        byte[] pngSignature = new byte[]{
                (byte) 0x89, 'P', 'N', 'G',
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };

        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "new.png", "image/png", pngSignature
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileStorageService.storeImage(avatar)).thenReturn("images/new.png");
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        userService.updateAvatar(1L, avatar);

        verify(fileStorageService).deleteFile("images/old-avatar.png");
        verify(fileStorageService).storeImage(avatar);
        verify(userRepository).save(user);
    }

    @Test
    void updateAvatar_rejectsInvalidImage() {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "document.txt", "text/plain",
                "not an image".getBytes()
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateAvatar(1L, avatar))
                .isInstanceOf(RuntimeException.class);

        verify(fileStorageService, never()).storeImage(any());
        verify(userRepository, never()).save(any());
    }

    // --------------------------------------------------
    // getUserStats
    // --------------------------------------------------

    @Test
    void getUserStats_returnsCalculatedStatistics() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(problemAttemptRepository.sumXpEarnedByUserId(1L)).thenReturn(150L);
        when(problemAttemptRepository.countSolvedByUserId(1L)).thenReturn(8L);
        when(questProgressRepository.findCompletedByUserId(1L))
                .thenReturn(List.of(mock(UserQuestProgress.class), mock(UserQuestProgress.class)));
        when(questProgressRepository.findByUserIdAndStatus(1L, QuestStatus.IN_PROGRESS))
                .thenReturn(List.of(mock(UserQuestProgress.class)));
        when(problemAttemptRepository.findByUserId(1L))
                .thenReturn(List.of(
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class),
                        mock(ProblemAttempt.class)
                ));
        when(commentRepository.countActiveByUserId(1L)).thenReturn(4L);

        UserStatsResponse result = userService.getUserStats(1L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getFullName()).isEqualTo("John Doe");
        assertThat(result.getTotalXpEarned()).isEqualTo(150L);
        assertThat(result.getProblemsSolved()).isEqualTo(8L);
        assertThat(result.getQuestsCompleted()).isEqualTo(2L);
        assertThat(result.getQuestsInProgress()).isEqualTo(1L);
        assertThat(result.getTotalProblemsAttempted()).isEqualTo(10L);
        assertThat(result.getAverageAccuracy()).isEqualTo(80.0);
        assertThat(result.getCommentsPosted()).isEqualTo(4L);
    }

    @Test
    void getUserStats_returnsZeroXpWhenSumIsNull() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(problemAttemptRepository.sumXpEarnedByUserId(1L)).thenReturn(null);
        when(problemAttemptRepository.countSolvedByUserId(1L)).thenReturn(0L);
        when(questProgressRepository.findCompletedByUserId(1L)).thenReturn(List.of());
        when(questProgressRepository.findByUserIdAndStatus(1L, QuestStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(problemAttemptRepository.findByUserId(1L)).thenReturn(List.of());
        when(commentRepository.countActiveByUserId(1L)).thenReturn(0L);

        UserStatsResponse result = userService.getUserStats(1L);

        assertThat(result.getTotalXpEarned()).isZero();
        assertThat(result.getAverageAccuracy()).isZero();
    }

    // --------------------------------------------------
    // changePassword
    // --------------------------------------------------

    @Test
    void changePassword_updatesPasswordAndRevokesRefreshTokens() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-password");
        when(userRepository.save(user)).thenReturn(user);

        userService.changePassword(1L, request);

        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void changePassword_rejectsIncorrectCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong-password");
        request.setNewPassword("new-password");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    // --------------------------------------------------
    // deactivateUser
    // --------------------------------------------------

    @Test
    void deactivateUser_deactivatesAccountAndRevokesTokens() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.deactivateUser(1L, 2L);

        assertThat(user.getActive()).isFalse();
        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(userRepository).save(user);
    }

    @Test
    void deactivateUser_rejectsDeactivatingOwnAccount() {
        assertThatThrownBy(() -> userService.deactivateUser(1L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot deactivate your own account");

        verifyNoInteractions(userRepository, refreshTokenRepository);
    }

    @Test
    void deactivateUser_rejectsDeactivatingLastActiveAdmin() {
        user.setRole(Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deactivateUser(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot deactivate the last active admin");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    // --------------------------------------------------
    // activateUser
    // --------------------------------------------------

    @Test
    void activateUser_activatesInactiveAccount() {
        user.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.activateUser(1L);

        assertThat(user.getActive()).isTrue();
        verify(userRepository).save(user);
    }
}
