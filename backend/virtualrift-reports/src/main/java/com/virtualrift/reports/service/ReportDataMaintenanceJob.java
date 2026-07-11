package com.virtualrift.reports.service;

import com.virtualrift.reports.config.ReportsDatabaseContext;
import com.virtualrift.reports.repository.OutboxEventRepository;
import com.virtualrift.reports.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ReportDataMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(ReportDataMaintenanceJob.class);
    private static final int PUBLISHED_EVENT_RETENTION_DAYS = 7;

    private final ReportRepository reportRepository;
    private final OutboxEventRepository outboxRepository;
    private final ReportsDatabaseContext databaseContext;

    public ReportDataMaintenanceJob(ReportRepository reportRepository,
                                    OutboxEventRepository outboxRepository,
                                    ReportsDatabaseContext databaseContext) {
        this.reportRepository = reportRepository;
        this.outboxRepository = outboxRepository;
        this.databaseContext = databaseContext;
    }

    @Scheduled(fixedDelayString = "${reports.maintenance.retention-cleanup-delay-ms:3600000}")
    @Transactional
    public void deleteExpiredData() {
        databaseContext.useRetentionMaintenance();
        Instant now = Instant.now();
        int reports = reportRepository.deleteExpiredBefore(now);
        int events = outboxRepository.deletePublishedBefore(
                now.minus(PUBLISHED_EVENT_RETENTION_DAYS, ChronoUnit.DAYS)
        );
        if (reports > 0 || events > 0) {
            log.info("Deleted {} expired reports and {} published report events", reports, events);
        }
    }
}
