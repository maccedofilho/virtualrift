package com.virtualrift.orchestrator.service;

import com.virtualrift.orchestrator.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class OutboxMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxMaintenanceJob.class);
    private static final int PUBLISHED_EVENT_RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxRepository;

    public OutboxMaintenanceJob(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${outbox.cleanup.fixed-delay-ms:3600000}")
    @Transactional
    public void deletePublishedEvents() {
        int deleted = outboxRepository.deletePublishedBefore(
                Instant.now().minus(PUBLISHED_EVENT_RETENTION_DAYS, ChronoUnit.DAYS)
        );
        if (deleted > 0) {
            log.info("Deleted {} published orchestrator outbox events", deleted);
        }
    }
}
