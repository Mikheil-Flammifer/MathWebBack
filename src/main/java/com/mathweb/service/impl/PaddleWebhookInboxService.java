package com.mathweb.service.impl;

import com.mathweb.repository.PaddleWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PaddleWebhookInboxService {

    private static final Logger log =
            LoggerFactory.getLogger(PaddleWebhookInboxService.class);

    private final PaddleWebhookEventRepository eventRepository;

    public PaddleWebhookInboxService(
            PaddleWebhookEventRepository eventRepository
    ) {
        this.eventRepository = eventRepository;
    }

    /**
     * @return true when inserted, false when the event already existed
     */
    @Transactional
    public boolean store(
            String eventId,
            String notificationId,
            String eventType,
            Instant occurredAt,
            String rawPayload
    ) {
        int inserted = eventRepository.insertIfAbsent(
                eventId,
                notificationId,
                eventType,
                occurredAt,
                rawPayload
        );

        if (inserted == 0) {
            log.info("Paddle event {} already exists", eventId);
            return false;
        }

        log.info("Stored Paddle event {} of type {}", eventId, eventType);
        return true;
    }
}