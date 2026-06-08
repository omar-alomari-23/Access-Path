package com.accesspath.services;

import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.Vote.VoteType;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfidenceService.
 * Covers score calculation logic: status bonus, vote contribution,
 * freshness decay, clamping, and the refreshCues update path.
 */
@ExtendWith(MockitoExtension.class)
class ConfidenceServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private VoteRepository voteRepository;

    @InjectMocks
    private ConfidenceService confidenceService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildReporter() {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("reporter@test.com")
                .passwordHash("hash")
                .role(UserRole.REPORTER)
                .build();
    }

    private Report buildReport(ReportStatus status, Instant createdAt) {
        return Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(buildReporter())
                .status(status)
                .category("OBSTRUCTION")
                .severity("MEDIUM")
                .createdAt(createdAt)
                .build();
    }

    // ── calculateScore: status bonus ─────────────────────────────────────────

    @Test
    @DisplayName("calculateScore: VERIFIED report, no votes, fresh → ~0.6")
    void calculateScore_verifiedFresh_noVotes_returnsExpectedScore() {
        Report report = buildReport(ReportStatus.VERIFIED, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(now)*0.2=0.2 → 0.6
        assertThat(score).isCloseTo(0.6f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: PENDING report, no votes, fresh → ~0.3")
    void calculateScore_pendingFresh_noVotes_returnsExpectedScore() {
        Report report = buildReport(ReportStatus.PENDING, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // PENDING=0.1 + votes=0 + freshness(now)*0.2=0.2 → 0.3
        assertThat(score).isCloseTo(0.3f, within(0.05f));
    }

    // ── calculateScore: vote contribution ────────────────────────────────────

    @Test
    @DisplayName("calculateScore: PENDING, 4 confirms, fresh → vote contribution capped at 0.4")
    void calculateScore_pendingWithManyConfirms_voteContributionCapped() {
        Report report = buildReport(ReportStatus.PENDING, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(10L); // 10*0.1=1.0 → capped at 0.4
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // PENDING=0.1 + votes(capped)=0.4 + freshness=0.2 → 0.7
        assertThat(score).isCloseTo(0.7f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: PENDING, 0 confirms, 1 dispute → dispute reduces net vote to 0 (floored)")
    void calculateScore_pendingWithDispute_voteScoreFlooredAtZero() {
        Report report = buildReport(ReportStatus.PENDING, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(1L); // -0.15 → floored to 0

        float score = confidenceService.calculateScore(report);

        // PENDING=0.1 + votes=0 (negative floored) + freshness=0.2 → 0.3
        assertThat(score).isCloseTo(0.3f, within(0.05f));
    }

    // ── calculateScore: freshness decay ──────────────────────────────────────

    @Test
    @DisplayName("calculateScore: VERIFIED report, created 48h ago → reduced freshness")
    void calculateScore_verifiedOld48h_reducedFreshness() {
        Instant twoDaysAgo = Instant.now().minus(48, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.VERIFIED, twoDaysAgo);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(48h=0.7)*0.2=0.14 → 0.54
        assertThat(score).isCloseTo(0.54f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: VERIFIED report, created 100h ago → lower freshness (0.4)")
    void calculateScore_verified100hOld_lowerFreshness() {
        Instant hundredHoursAgo = Instant.now().minus(100, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.VERIFIED, hundredHoursAgo);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(100h=0.4)*0.2=0.08 → 0.48
        assertThat(score).isCloseTo(0.48f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: VERIFIED report, created 200h ago → lowest freshness (0.1)")
    void calculateScore_verifiedVeryOld_lowestFreshness() {
        Instant veryOld = Instant.now().minus(200, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.VERIFIED, veryOld);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(200h=0.1)*0.2=0.02 → 0.42
        assertThat(score).isCloseTo(0.42f, within(0.05f));
    }

    // ── calculateScore: clamping ──────────────────────────────────────────────

    @Test
    @DisplayName("calculateScore: score clamped to maximum 1.0")
    void calculateScore_highInputs_clampedToOne() {
        Report report = buildReport(ReportStatus.VERIFIED, Instant.now());

        // VERIFIED=0.4 + confirms(4*0.1=0.4, capped at 0.4) + freshness=0.2 → 1.0
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(4L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        assertThat(score).isLessThanOrEqualTo(1.0f);
    }

    @Test
    @DisplayName("calculateScore: score never goes below 0.0")
    void calculateScore_lowestPossibleInputs_clampedToZero() {
        // REJECTED status → score contribution = 0, ancient report
        Instant ancient = Instant.now().minus(300, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.REJECTED, ancient);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        assertThat(score).isGreaterThanOrEqualTo(0.0f);
    }

    // ── calculateScore: freshness tier boundary values ───────────────────────

    @Test
    @DisplayName("calculateScore: VERIFIED report, exactly 24h old → freshness drops to 0.7 tier")
    void calculateScore_verified_exactly24hOld_freshness07() {
        Instant exactly24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.VERIFIED, exactly24h);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(>=24h → 0.7)*0.2=0.14 → 0.54
        assertThat(score).isCloseTo(0.54f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: VERIFIED report, exactly 72h old → freshness drops to 0.4 tier")
    void calculateScore_verified_exactly72hOld_freshness04() {
        Instant exactly72h = Instant.now().minus(72, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.VERIFIED, exactly72h);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(>=72h → 0.4)*0.2=0.08 → 0.48
        assertThat(score).isCloseTo(0.48f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: VERIFIED report, exactly 168h old → freshness drops to 0.1 tier")
    void calculateScore_verified_exactly168hOld_freshness01() {
        Instant exactly168h = Instant.now().minus(168, ChronoUnit.HOURS);
        Report report = buildReport(ReportStatus.VERIFIED, exactly168h);

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // VERIFIED=0.4 + votes=0 + freshness(>=168h → 0.1)*0.2=0.02 → 0.42
        assertThat(score).isCloseTo(0.42f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: EXPIRED report, fresh, no votes → status bonus is 0, score ~0.2")
    void calculateScore_expired_fresh_noVotes_statusBonusIsZero() {
        Report report = buildReport(ReportStatus.EXPIRED, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // EXPIRED has no status bonus (0) + votes=0 + freshness(1.0)*0.2=0.2
        assertThat(score).isCloseTo(0.2f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: RESOLVED report, fresh, no votes → status bonus is 0, score ~0.2")
    void calculateScore_resolved_fresh_noVotes_statusBonusIsZero() {
        Report report = buildReport(ReportStatus.RESOLVED, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // RESOLVED has no status bonus (0) + votes=0 + freshness(1.0)*0.2=0.2
        assertThat(score).isCloseTo(0.2f, within(0.05f));
    }

    @Test
    @DisplayName("calculateScore: REJECTED report, fresh → status bonus is 0, score ~0.2")
    void calculateScore_rejected_fresh_statusBonusIsZero() {
        Report report = buildReport(ReportStatus.REJECTED, Instant.now());

        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);

        float score = confidenceService.calculateScore(report);

        // REJECTED=0.0 + votes=0 + freshness(1.0)*0.2=0.2
        assertThat(score).isCloseTo(0.2f, within(0.05f));
    }

    // ── refreshCues ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("refreshCues: valid report → recalculates and saves score")
    void refreshCues_validReport_savesUpdatedScore() {
        Report report = buildReport(ReportStatus.PENDING, Instant.now());

        when(reportRepository.findById(report.getReportId()))
                .thenReturn(Optional.of(report));
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.CONFIRM)).thenReturn(0L);
        when(voteRepository.countByReport_ReportIdAndVoteType(
                report.getReportId(), VoteType.DISPUTE)).thenReturn(0L);
        when(reportRepository.save(any())).thenReturn(report);

        confidenceService.refreshCues(report.getReportId());

        verify(reportRepository).save(report);
        assertThat(report.getConfidenceScore()).isNotNull();
    }

    @Test
    @DisplayName("refreshCues: unknown report ID → ResourceNotFoundException")
    void refreshCues_unknownReport_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(reportRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> confidenceService.refreshCues(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reportRepository, never()).save(any());
    }
}
