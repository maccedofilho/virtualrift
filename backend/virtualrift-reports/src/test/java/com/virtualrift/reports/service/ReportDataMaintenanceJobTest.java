package com.virtualrift.reports.service;

import com.virtualrift.reports.config.ReportsDatabaseContext;
import com.virtualrift.reports.repository.OutboxEventRepository;
import com.virtualrift.reports.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportDataMaintenanceJob Tests")
class ReportDataMaintenanceJobTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ReportsDatabaseContext databaseContext;

    private ReportDataMaintenanceJob job;

    @BeforeEach
    void setUp() {
        job = new ReportDataMaintenanceJob(reportRepository, outboxRepository, databaseContext);
    }

    @Test
    @DisplayName("should enable retention context before deleting expired data")
    void deleteExpiredData_quandoChamado_usaContextoDeRetencao() {
        job.deleteExpiredData();

        verify(databaseContext).useRetentionMaintenance();
        verify(reportRepository).deleteExpiredBefore(any(Instant.class));
        verify(outboxRepository).deletePublishedBefore(any(Instant.class));
    }
}
