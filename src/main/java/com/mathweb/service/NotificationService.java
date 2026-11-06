package com.mathweb.service;

import com.mathweb.dto.response.NotificationResponse;
import com.mathweb.dto.response.PageResponse;
import com.mathweb.enums.NotificationType;

public interface NotificationService {

    /** Called by other services. Joins the caller's transaction. */
    void notify(Long userId, NotificationType type, String title, String message, String link);

    /** Same, but silently skipped if this user already got a notification with this key. */
    void notifyOnce(Long userId, NotificationType type, String title, String message,
                    String link, String dedupeKey);

    void notifyAllStudents(NotificationType type, String title, String message, String link);

    PageResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size);
    long unreadCount(Long userId);
    NotificationResponse markRead(Long id, Long userId);
    int markAllRead(Long userId);
    void delete(Long id, Long userId);
    int purgeOlderThan(int days);
}