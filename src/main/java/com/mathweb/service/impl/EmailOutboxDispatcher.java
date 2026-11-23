package com.mathweb.service.impl;

import com.mathweb.entity.EmailOutbox;
import com.mathweb.enums.EmailDeliveryStatus;
import com.mathweb.repository.EmailOutboxRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Component
@ConditionalOnProperty(
        name = "app.mail.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EmailOutboxDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(EmailOutboxDispatcher.class);

    private static final Duration[] BACKOFF = {
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6)
    };

    private final String owner = "mail-outbox-" + UUID.randomUUID();

    private final EmailOutboxRepository outboxRepository;
    private final JavaMailSender mailSender;
    private final EmailOutboxPayloadCipher payloadCipher;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate tx;
    private final String mailFrom;
    private final int batchSize;
    private final int maxAttempts;
    private final int leaseSeconds;

    public EmailOutboxDispatcher(
            EmailOutboxRepository outboxRepository,
            JavaMailSender mailSender,
            EmailOutboxPayloadCipher payloadCipher,
            @Qualifier("mailTaskExecutor") TaskExecutor taskExecutor,
            PlatformTransactionManager transactionManager,
            @Value("${app.mail-from}") String mailFrom,
            @Value("${app.mail.outbox.batch-size:20}") int batchSize,
            @Value("${app.mail.outbox.max-attempts:5}") int maxAttempts,
            @Value("${app.mail.outbox.lease-seconds:120}") int leaseSeconds
    ) {
        this.outboxRepository = outboxRepository;
        this.mailSender = mailSender;
        this.payloadCipher = payloadCipher;
        this.taskExecutor = taskExecutor;
        this.tx = new TransactionTemplate(transactionManager);
        this.mailFrom = mailFrom;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(
            initialDelayString = "${app.mail.outbox.initial-delay-ms:5000}",
            fixedDelayString = "${app.mail.outbox.poll-ms:5000}"
    )
    public void poll() {
        List<Long> ids = tx.execute(status ->
                outboxRepository.claimBatch(
                        owner,
                        leaseSeconds,
                        batchSize
                )
        );

        if (ids == null) {
            return;
        }

        for (Long id : ids) {
            try {
                taskExecutor.execute(() -> process(id));
            } catch (RejectedExecutionException exception) {
                release(id);
                log.warn(
                        "Mail worker queue is full; row {} will be retried",
                        id
                );
            }
        }
    }

    private void process(Long id) {
        try {
            EmailOutbox outbox = tx.execute(status ->
                    outboxRepository.findById(id)
                            .filter(this::ownsLease)
                            .orElse(null)
            );

            if (outbox == null) {
                return;
            }

            EmailOutboxPayloadCipher.EmailPayload payload =
                    payloadCipher.decrypt(outbox.getPayload());

            if (payload.expiresAt() != null
                    && !Instant.now().isBefore(payload.expiresAt())) {
                markExpired(id);
                return;
            }

            send(payload);
            markSent(id);
        } catch (RuntimeException exception) {
            log.warn(
                    "Mail outbox row {} failed: type={}",
                    id,
                    exception.getClass().getSimpleName()
            );

            recordFailure(id, exception);
        }
    }

    private void send(EmailOutboxPayloadCipher.EmailPayload payload) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(payload.toEmail());
            helper.setSubject(payload.subject());
            helper.setText(payload.htmlBody(), true);

            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException(
                    "SMTP submission failed",
                    exception
            );
        }
    }

    private void markSent(Long id) {
        tx.executeWithoutResult(status ->
                outboxRepository.findById(id)
                        .filter(this::ownsLease)
                        .ifPresent(outbox -> {
                            outbox.setStatus(EmailDeliveryStatus.SENT);
                            outbox.setSentAt(Instant.now());
                            outbox.setLastError(null);

                            clearLease(outbox);
                            outboxRepository.save(outbox);
                        })
        );
    }

    private void markExpired(Long id) {
        tx.executeWithoutResult(status ->
                outboxRepository.findById(id)
                        .filter(this::ownsLease)
                        .ifPresent(outbox -> {
                            outbox.setStatus(EmailDeliveryStatus.FAILED);
                            outbox.setLastError(
                                    "Message expired before SMTP submission"
                            );

                            clearLease(outbox);
                            outboxRepository.save(outbox);
                        })
        );
    }

    private void recordFailure(Long id, RuntimeException error) {
        try {
            tx.executeWithoutResult(status ->
                    outboxRepository.findById(id)
                            .filter(this::ownsLease)
                            .ifPresent(outbox -> {
                                // SMTP error text can contain recipient data.
                                outbox.setLastError(
                                        error.getClass().getSimpleName()
                                );

                                if (outbox.getAttemptCount()
                                        >= maxAttempts) {
                                    outbox.setStatus(
                                            EmailDeliveryStatus.FAILED
                                    );

                                    log.error(
                                            "Mail outbox row {} exhausted {} "
                                                    + "delivery attempts",
                                            outbox.getId(),
                                            outbox.getAttemptCount()
                                    );
                                } else {
                                    int index = Math.min(
                                            outbox.getAttemptCount() - 1,
                                            BACKOFF.length - 1
                                    );

                                    outbox.setStatus(
                                            EmailDeliveryStatus.RETRY
                                    );

                                    outbox.setNextAttemptAt(
                                            Instant.now()
                                                    .plus(BACKOFF[index])
                                    );
                                }

                                clearLease(outbox);
                                outboxRepository.save(outbox);
                            })
            );
        } catch (RuntimeException persistenceError) {
            log.error(
                    "Could not record failure for mail outbox row {}",
                    id,
                    persistenceError
            );
        }
    }

    private void release(Long id) {
        tx.executeWithoutResult(status ->
                outboxRepository.findById(id)
                        .filter(this::ownsLease)
                        .ifPresent(outbox -> {
                            outbox.setStatus(EmailDeliveryStatus.RETRY);
                            outbox.setNextAttemptAt(Instant.now());

                            clearLease(outbox);
                            outboxRepository.save(outbox);
                        })
        );
    }

    private boolean ownsLease(EmailOutbox outbox) {
        return outbox.getStatus() == EmailDeliveryStatus.PROCESSING
                && owner.equals(outbox.getProcessingOwner());
    }

    private static void clearLease(EmailOutbox outbox) {
        outbox.setProcessingOwner(null);
        outbox.setProcessingStartedAt(null);
        outbox.setProcessingLeaseUntil(null);
        outbox.setUpdatedAt(Instant.now());
    }
}