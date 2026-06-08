package com.accesspath.scheduler;

import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.services.ConfidenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportExpiryScheduler.
 * Covers the TTL expiry logic: PENDING and VERIFIED reports past their
 * expiresAt are set to EXPIRED; REJECTED and RESOLVED reports are skipped.
 */
@ExtendWith(MockitoExtension.class)
class ReportExpirySchedulerTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ConfidenceService confidenceService;

    @InjectMocks
    private ReportExpiryScheduler scheduler;

    /** Builds a real Report (not a mock) so setStatus() calls work. */
    private Report buildReport(ReportStatus status) {
        return Report.builder()
                .reportId(UUID.randomUUID())
                .status(status)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
    }

    // ── no expired reports ────────────────────────────────────────────────────

    @Test
    @DisplayName("expireStaleReports: no expired reports → nothing saved or confidence-refreshed")
    void expireStaleReports_noExpiredReports_doesNothing() {
        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of());

        scheduler.expireStaleReports();

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(confidenceService);
    }

    // ── PENDING / VERIFIED expired → must be set to EXPIRED ──────────────────

    @Test
    @DisplayName("expireStaleReports: PENDING report past TTL → status set to EXPIRED and saved")
    void expireStaleReports_pendingExpired_setsExpiredStatus() {
        Report pending = buildReport(ReportStatus.PENDING);
        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(pending));

        scheduler.expireStaleReports();

        assertThat(pending.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        verify(reportRepository).save(pending);
        verify(confidenceService).refreshCues(pending.getReportId());
    }

    @Test
    @DisplayName("expireStaleReports: VERIFIED report past TTL → status set to EXPIRED and saved")
    void expireStaleReports_verifiedExpired_setsExpiredStatus() {
        Report verified = buildReport(ReportStatus.VERIFIED);
        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(verified));

        scheduler.expireStaleReports();

        assertThat(verified.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        verify(reportRepository).save(verified);
        verify(confidenceService).refreshCues(verified.getReportId());
    }

    // ── REJECTED / RESOLVED → must be skipped ────────────────────────────────

    @Test
    @DisplayName("expireStaleReports: REJECTED report past TTL → not changed, not saved")
    void expireStaleReports_rejectedReport_isSkipped() {
        Report rejected = buildReport(ReportStatus.REJECTED);
        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(rejected));

        scheduler.expireStaleReports();

        assertThat(rejected.getStatus()).isEqualTo(ReportStatus.REJECTED);
        verify(reportRepository, never()).save(any());
        verifyNoInteractions(confidenceService);
    }

    @Test
    @DisplayName("expireStaleReports: RESOLVED report past TTL → not changed, not saved")
    void expireStaleReports_resolvedReport_isSkipped() {
        Report resolved = buildReport(ReportStatus.RESOLVED);
        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(resolved));

        scheduler.expireStaleReports();

        assertThat(resolved.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(reportRepository, never()).save(any());
        verifyNoInteractions(confidenceService);
    }

    // ── mixed statuses ────────────────────────────────────────────────────────

    @Test
    @DisplayName("expireStaleReports: mix of statuses → only PENDING and VERIFIED are expired")
    void expireStaleReports_mixedStatuses_onlyActiveReportsExpired() {
        Report pending  = buildReport(ReportStatus.PENDING);
        Report verified = buildReport(ReportStatus.VERIFIED);
        Report rejected = buildReport(ReportStatus.REJECTED);
        Report resolved = buildReport(ReportStatus.RESOLVED);

        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(pending, verified, rejected, resolved));

        scheduler.expireStaleReports();

        assertThat(pending.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(verified.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(rejected.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(resolved.getStatus()).isEqualTo(ReportStatus.RESOLVED);

        verify(reportRepository).save(pending);
        verify(reportRepository).save(verified);
        verify(reportRepository, never()).save(rejected);
        verify(reportRepository, never()).save(resolved);

        verify(confidenceService).refreshCues(pending.getReportId());
        verify(confidenceService).refreshCues(verified.getReportId());
        verify(confidenceService, never()).refreshCues(rejected.getReportId());
        verify(confidenceService, never()).refreshCues(resolved.getReportId());
    }

    // ── exception handling ────────────────────────────────────────────────────

    @Test
    @DisplayName("expireStaleReports: repository query throws → exception propagates, nothing saved")
    void expireStaleReports_repositoryThrows_propagatesException() {
        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenThrow(new RuntimeException("DB connection error"));

        assertThatThrownBy(() -> scheduler.expireStaleReports())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection error");

        verify(reportRepository, never()).save(any());
        verifyNoInteractions(confidenceService);
    }

    @Test
    @DisplayName("expireStaleReports: refreshCues throws for first report → loop stops, second report not processed")
    void expireStaleReports_refreshCuesThrows_stopsProcessing() {
        Report r1 = buildReport(ReportStatus.PENDING);
        Report r2 = buildReport(ReportStatus.PENDING);

        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(r1, r2));
        doThrow(new RuntimeException("Confidence service error"))
                .when(confidenceService).refreshCues(r1.getReportId());

        assertThatThrownBy(() -> scheduler.expireStaleReports())
                .isInstanceOf(RuntimeException.class);

        // r1 status was set and saved before refreshCues threw
        assertThat(r1.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        verify(reportRepository).save(r1);
        // r2 was never reached
        verify(reportRepository, never()).save(r2);
    }

    @Test
    @DisplayName("expireStaleReports: multiple PENDING reports → all expired and confidence-refreshed")
    void expireStaleReports_multiplePending_allExpired() {
        Report r1 = buildReport(ReportStatus.PENDING);
        Report r2 = buildReport(ReportStatus.PENDING);
        Report r3 = buildReport(ReportStatus.PENDING);

        when(reportRepository.findByExpiresAtBeforeAndStatusNot(any(), any()))
                .thenReturn(List.of(r1, r2, r3));

        scheduler.expireStaleReports();

        assertThat(r1.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(r2.getStatus()).isEqualTo(ReportStatus.EXPIRED);
        assertThat(r3.getStatus()).isEqualTo(ReportStatus.EXPIRED);

        verify(reportRepository, times(3)).save(any());
        verify(confidenceService).refreshCues(r1.getReportId());
        verify(confidenceService).refreshCues(r2.getReportId());
        verify(confidenceService).refreshCues(r3.getReportId());
    }
}
