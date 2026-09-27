package com.mathweb.service.impl;

import com.mathweb.dto.request.ChangePasswordRequest;
import com.mathweb.dto.request.UpdateProfileRequest;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.dto.response.UserResponse;
import com.mathweb.dto.response.UserStatsResponse;
import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.mapper.UserMapper;
import com.mathweb.repository.CommentRepository;
import com.mathweb.repository.ProblemAttemptRepository;
import com.mathweb.repository.UserQuestProgressRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.FileStorageService;
import com.mathweb.service.UserService;
import com.mathweb.util.FileUtil;
import com.mathweb.util.SanitizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final ProblemAttemptRepository problemAttemptRepository;
    private final UserQuestProgressRepository questProgressRepository;
    private final CommentRepository commentRepository;

    public UserServiceImpl(UserRepository userRepository,
                           UserMapper userMapper,
                           FileStorageService fileStorageService,
                           PasswordEncoder passwordEncoder,
                           ProblemAttemptRepository problemAttemptRepository,
                           UserQuestProgressRepository questProgressRepository,
                           CommentRepository commentRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
        this.problemAttemptRepository = problemAttemptRepository;
        this.questProgressRepository = questProgressRepository;
        this.commentRepository = commentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return getUserById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findAll(pageRequest);

        return PageResponse.<UserResponse>builder()
                .content(userPage.getContent().stream()
                        .map(this::mapToUserResponse).toList())
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setRole(role);
        userRepository.save(user);
        log.info("User {} role updated to {}", userId, role);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId,
                                      UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.getFirstName() != null) {
            user.setFirstName(
                    SanitizationUtil.sanitizeText(request.getFirstName()));
        }
        if (request.getLastName() != null) {
            user.setLastName(
                    SanitizationUtil.sanitizeText(request.getLastName()));
        }

        userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateAvatar(Long userId, MultipartFile avatar) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        FileUtil.validateImage(avatar);

        // Delete old avatar
        if (user.getAvatarUrl() != null) {
            fileStorageService.deleteFile(user.getAvatarUrl());
        }

        String avatarPath = fileStorageService.storeImage(avatar);
        user.setAvatarUrl(avatarPath);
        userRepository.save(user);

        log.info("Avatar updated for user {}", userId);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        long totalXp = problemAttemptRepository
                .sumXpEarnedByUserId(userId) != null
                ? problemAttemptRepository.sumXpEarnedByUserId(userId) : 0L;

        long problemsSolved = problemAttemptRepository
                .countSolvedByUserId(userId);

        long questsCompleted = questProgressRepository
                .findCompletedByUserId(userId).size();

        long questsInProgress = questProgressRepository
                .findByUserIdAndStatus(userId,
                        com.mathweb.enums.QuestStatus.IN_PROGRESS).size();

        long totalAttempted = problemAttemptRepository
                .findByUserId(userId).size();

        double accuracy = totalAttempted > 0
                ? (double) problemsSolved / totalAttempted * 100.0 : 0.0;

        long commentsPosted = commentRepository
                .countActiveByUserId(userId);

        return UserStatsResponse.builder()
                .userId(userId)
                .fullName(user.getFullName())
                .totalXpEarned(totalXp)
                .problemsSolved(problemsSolved)
                .questsCompleted(questsCompleted)
                .questsInProgress(questsInProgress)
                .totalProblemsAttempted(totalAttempted)
                .averageAccuracy(Math.round(accuracy * 10.0) / 10.0)
                .commentsPosted(commentsPosted)
                .build();
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(),
                user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(
                passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user {}", userId);
    }

    @Override
    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setActive(false);
        userRepository.save(user);
        log.info("User {} deactivated", userId);
    }

    @Override
    @Transactional
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setActive(true);
        userRepository.save(user);
        log.info("User {} activated", userId);
    }

    private UserResponse mapToUserResponse(User user) {
        return userMapper.toResponse(user);
    }
}