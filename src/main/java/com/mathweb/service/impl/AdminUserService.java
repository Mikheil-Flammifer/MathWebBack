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
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.AccessGrantRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.impl.AccessGrantService;
import com.mathweb.service.FileStorageService;
import com.mathweb.util.PageUtil;
import com.mathweb.util.SanitizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int HISTORY_LIMIT = 50;

    private final UserRepository userRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final AccessGrantService accessGrantService;
    private final FileStorageService fileStorageService;

    public AdminUserService(UserRepository userRepository,
                            AccessGrantRepository accessGrantRepository,
                            AccessGrantService accessGrantService,
                            FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.accessGrantService = accessGrantService;
        this.fileStorageService = fileStorageService;
    }

    // ===== READ =====

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(int page, int size, String keyword,
                                                     Role role, Boolean active, Boolean hasAccess) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));

        Page<User> users = userRepository.searchForAdmin(
                normalizeKeyword(keyword), role, active, hasAccess, LocalDateTime.now(), pageable);

        return PageUtil.toPageResponse(users, u -> toResponse(u, u.getSubscription()));
    }

    @Transactional(readOnly = true)
    public List<AccessGrantResponse> getHistory(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        List<AccessGrant> rows = accessGrantRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                userId, PageRequest.of(0, HISTORY_LIMIT));

        Map<Long, String> adminNames = new HashMap<>();
        rows.stream().map(AccessGrant::getGrantedBy).filter(Objects::nonNull).distinct()
                .forEach(id -> adminNames.put(id, null));
        userRepository.findAllById(adminNames.keySet())
                .forEach(u -> adminNames.put(u.getId(), u.getFullName()));

        return rows.stream().map(g -> AccessGrantResponse.builder()
                .id(g.getId())
                .action(g.getAction())
                .days(g.getDays())
                .note(g.getNote())
                .grantedById(g.getGrantedBy())
                .grantedByName(adminNames.get(g.getGrantedBy()))
                .accessEndsAt(g.getPeriodEndAfter())
                .createdAt(g.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    // ===== WRITE =====

    @Transactional
    public AdminUserResponse grantAccess(Long adminId, Long userId, GrantAccessRequest request) {
        User user = findUser(userId);
        Subscription sub = accessGrantService.grantFreeDays(userId, request.getDays());

        accessGrantRepository.save(AccessGrant.builder()
                .userId(userId)
                .grantedBy(adminId)
                .action(AccessGrantAction.GRANT)
                .days(request.getDays())
                .note(cleanNote(request.getNote()))
                .periodEndAfter(sub.getCurrentPeriodEnd())
                .build());

        log.info("Admin {} granted {} free days to user {}", adminId, request.getDays(), userId);
        return toResponse(user, sub);
    }

    @Transactional
    public AdminUserResponse revokeAccess(Long adminId, Long userId, String note) {
        User user = findUser(userId);
        Subscription sub = accessGrantService.revoke(userId);

        accessGrantRepository.save(AccessGrant.builder()
                .userId(userId)
                .grantedBy(adminId)
                .action(AccessGrantAction.REVOKE)
                .note(cleanNote(note))
                .periodEndAfter(sub.getCurrentPeriodEnd())
                .build());

        log.info("Admin {} revoked access of user {}", adminId, userId);
        return toResponse(user, sub);
    }

    // ===== HELPERS =====

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        String k = keyword.trim();
        return k.length() > MAX_KEYWORD_LENGTH ? k.substring(0, MAX_KEYWORD_LENGTH) : k;
    }

    private String cleanNote(String note) {
        String n = SanitizationUtil.cleanText(note);
        if (n == null || n.isBlank()) return null;
        return n.length() > MAX_NOTE_LENGTH ? n.substring(0, MAX_NOTE_LENGTH) : n;
    }

    private AdminUserResponse toResponse(User u, Subscription sub) {
        return AdminUserResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .role(u.getRole())
                .emailVerified(u.getEmailVerified())
                .active(u.getActive())
                .avatarUrl(fileStorageService.getFileUrl(u.getAvatarUrl()))
                .createdAt(u.getCreatedAt())
                .hasActiveAccess(sub != null && sub.isCurrentlyActive())
                .subscriptionStatus(sub != null ? sub.getStatus() : null)
                .accessEndsAt(sub != null ? sub.getCurrentPeriodEnd() : null)
                .build();
    }
}