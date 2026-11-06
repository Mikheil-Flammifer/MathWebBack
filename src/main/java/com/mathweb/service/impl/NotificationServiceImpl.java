package com.mathweb.service.impl;

import com.mathweb.dto.response.NotificationResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.entity.Notification;
import com.mathweb.enums.NotificationType;
import com.mathweb.enums.Role;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.NotificationRepository;
import com.mathweb.repository.UserRepository;
import com.mathweb.service.NotificationService;
import com.mathweb.util.PageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ===== CREATE =====

    @Override
    @Transactional
    public void notify(Long userId, NotificationType type, String title, String message, String link) {
        notificationRepository.save(build(userId, type, title, message, link, null));
    }

    @Override
    @Transactional
    public void notifyOnce(Long userId, NotificationType type, String title, String message,
                           String link, String dedupeKey) {
        if (notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) return;
        notificationRepository.save(build(userId, type, title, message, link, dedupeKey));
    }

    @Override
    @Transactional
    public void notifyAllStudents(NotificationType type, String title, String message, String link) {
        List<Long> ids = userRepository.findActiveVerifiedIdsByRole(Role.STUDENT);
        notificationRepository.saveAll(ids.stream()
                .map(id -> build(id, type, title, message, link, null))
                .toList());
        log.info("Notification '{}' sent to {} students", title, ids.size());
    }

    // ===== READ / UPDATE =====

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndIsReadFalse(userId, pr)
                : notificationRepository.findByUserId(userId, pr);
        return PageUtil.toPageResponse(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long id, Long userId) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (!Boolean.TRUE.equals(n.getIsRead())) {
            n.setIsRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
        return toResponse(n);
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notificationRepository.delete(n);
    }

    @Override
    @Transactional
    public int purgeOlderThan(int days) {
        return notificationRepository.deleteOlderThan(LocalDateTime.now().minusDays(days));
    }

    // ===== HELPERS =====

    private Notification build(Long userId, NotificationType type, String title,
                               String message, String link, String dedupeKey) {
        return Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .dedupeKey(dedupeKey)
                .build();
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .link(n.getLink())
                .read(Boolean.TRUE.equals(n.getIsRead()))
                .createdAt(n.getCreatedAt())
                .build();
    }
}