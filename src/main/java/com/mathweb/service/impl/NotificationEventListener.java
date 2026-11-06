package com.mathweb.service.impl;

import com.mathweb.enums.NotificationType;
import com.mathweb.event.VideoPublishedEvent;
import com.mathweb.service.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVideoPublished(VideoPublishedEvent e) {
        notificationService.notifyAllStudents(
                NotificationType.NEW_VIDEO,
                "New video available",
                e.title(),
                "/videos/" + e.videoId());   // ADAPT to your frontend route
    }
}