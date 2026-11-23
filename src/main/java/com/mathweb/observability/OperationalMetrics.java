package com.mathweb.observability;

import com.mathweb.enums.EmailDeliveryStatus;
import com.mathweb.enums.PaddleWebhookEventStatus;
import com.mathweb.repository.EmailOutboxRepository;
import com.mathweb.repository.PaddleWebhookEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OperationalMetrics {

    private static final Logger log =
            LoggerFactory.getLogger(OperationalMetrics.class);

    private final JdbcTemplate jdbc;
    private final EmailOutboxRepository emailOutboxRepository;
    private final PaddleWebhookEventRepository webhookEventRepository;
    private final Path localMediaPath;
    private final Counter authenticationFailures;

    private final AtomicInteger databaseUp = new AtomicInteger();
    private final AtomicLong localStorageFree = new AtomicLong();
    private final AtomicLong localStorageTotal = new AtomicLong();

    private final EnumMap<EmailDeliveryStatus, AtomicLong> mailRows =
            new EnumMap<>(EmailDeliveryStatus.class);

    private final EnumMap<PaddleWebhookEventStatus, AtomicLong> webhookRows =
            new EnumMap<>(PaddleWebhookEventStatus.class);

    public OperationalMetrics(
            MeterRegistry registry,
            JdbcTemplate jdbc,
            EmailOutboxRepository emailOutboxRepository,
            PaddleWebhookEventRepository webhookEventRepository,
            @Value("${file.upload.base-path:./uploads}") String localMediaPath
    ) {
        this.jdbc = jdbc;
        this.emailOutboxRepository = emailOutboxRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.localMediaPath = Paths.get(localMediaPath)
                .toAbsolutePath()
                .normalize();

        this.authenticationFailures = Counter.builder("mathweb.auth.failures")
                .description("Failed authentication attempts")
                .register(registry);

        Gauge.builder("mathweb.database.up", databaseUp, AtomicInteger::get)
                .description("Whether a lightweight database query succeeds")
                .register(registry);

        Gauge.builder(
                        "mathweb.storage.local.free",
                        localStorageFree,
                        AtomicLong::get
                )
                .baseUnit("bytes")
                .description("Free bytes on the filesystem holding local uploads")
                .register(registry);

        Gauge.builder(
                        "mathweb.storage.local.total",
                        localStorageTotal,
                        AtomicLong::get
                )
                .baseUnit("bytes")
                .description("Total bytes on the filesystem holding local uploads")
                .register(registry);

        for (EmailDeliveryStatus status : EmailDeliveryStatus.values()) {
            AtomicLong count = new AtomicLong();
            mailRows.put(status, count);

            Gauge.builder("mathweb.mail.outbox.rows", count, AtomicLong::get)
                    .tag(
                            "status",
                            status.name().toLowerCase(java.util.Locale.ROOT)
                    )
                    .description("Email outbox rows by status")
                    .register(registry);
        }

        for (PaddleWebhookEventStatus status :
                PaddleWebhookEventStatus.values()) {
            AtomicLong count = new AtomicLong();
            webhookRows.put(status, count);

            Gauge.builder("mathweb.webhook.events", count, AtomicLong::get)
                    .tag(
                            "status",
                            status.name().toLowerCase(java.util.Locale.ROOT)
                    )
                    .description("Paddle webhook inbox rows by status")
                    .register(registry);
        }
    }

    @PostConstruct
    void initialize() {
        refresh();
    }

    public void recordAuthenticationFailure() {
        authenticationFailures.increment();
    }

    @Scheduled(fixedDelayString = "${app.metrics.refresh-ms:30000}")
    public void refresh() {
        refreshDatabaseStatus();
        refreshQueueCounts();
        refreshLocalStorage();
    }

    private void refreshDatabaseStatus() {
        try {
            jdbc.queryForObject("select 1", Integer.class);
            databaseUp.set(1);
        } catch (RuntimeException exception) {
            databaseUp.set(0);
            log.warn(
                    "Database health metric refresh failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void refreshQueueCounts() {
        try {
            for (EmailDeliveryStatus status : EmailDeliveryStatus.values()) {
                mailRows.get(status)
                        .set(emailOutboxRepository.countByStatus(status));
            }

            for (PaddleWebhookEventStatus status :
                    PaddleWebhookEventStatus.values()) {
                webhookRows.get(status)
                        .set(webhookEventRepository.countByStatus(status));
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Queue metric refresh failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void refreshLocalStorage() {
        try {
            Path existingPath = localMediaPath;

            while (!Files.exists(existingPath)
                    && existingPath.getParent() != null) {
                existingPath = existingPath.getParent();
            }

            FileStore store = Files.getFileStore(existingPath);
            localStorageFree.set(store.getUsableSpace());
            localStorageTotal.set(store.getTotalSpace());
        } catch (IOException exception) {
            log.warn(
                    "Local storage metric refresh failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}