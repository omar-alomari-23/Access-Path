package com.accesspath.services;

import com.accesspath.dto.ModerationActionDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.models.ModerationAction;
import com.accesspath.models.ModerationAction.ActionType;
import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.ModerationActionRepository;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ModerationService.
 * Covers US-7/US-8: moderation queue and actions (verify, reject, resolve, expire).
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private ModerationActionRepository moderationActionRepository;
    @Mock private ConfidenceService confidenceService;

    @InjectMocks
    private ModerationService moderationService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildModerator() {
        return User.builder()
                .userId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .email("mod@test.com")
                .passwordHash("hash")
                .role(UserRole.MODERATOR)
                .build();
    }

    private User buildReporter() {
        return User.builder()
                .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("reporter@test.com")
                .passwordHash("hash")
                .role(UserRole.REPORTER)
                .build();
    }

    private Report buildReport(User reporter, ReportStatus status) {
        return Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(status)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .description("Test report")
                .confidenceScore(0.5f)
                .createdAt(Instant.now())
                .build();
    }

    private ModerationActionDTO.ActionRequest note(String text) {
        ModerationActionDTO.ActionRequest r = new ModerationActionDTO.ActionRequest();
        r.setNote(text);
        return r;
    }

    private ModerationAction savedAction(Report report, User mod, ActionType type) {
        return ModerationAction.builder()
                .actionId(UUID.randomUUID())
                .report(report)
                .moderator(mod)
                .actionType(type)
                .note("test note")
                .createdAt(Instant.now())
                .build();
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify: PENDING report → status set to VERIFIED, action saved")
    void verify_pendingReport_setsVerifiedAndSavesAction() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.PENDING);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));
        when(reportRepository.save(any())).thenReturn(report);
        when(moderationActionRepository.save(any())).thenReturn(savedAction(report, mod, ActionType.VERIFY));

        ModerationActionDTO.Response response =
                moderationService.verify(report.getReportId(), mod.getUserId(), note("Confirmed on site"));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.VERIFIED);
        assertThat(response.getActionType()).isEqualTo(ActionType.VERIFY);
        verify(confidenceService).refreshCues(report.getReportId());
    }

    @Test
    @DisplayName("verify: already VERIFIED report → IllegalStateException")
    void verify_alreadyVerifiedReport_throwsIllegalStateException() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.VERIFIED);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));

        assertThatThrownBy(() ->
                moderationService.verify(report.getReportId(), mod.getUserId(), note(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFY requires PENDING");
    }

    // ── reject ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reject: PENDING report → status set to REJECTED, action saved")
    void reject_pendingReport_setsRejectedAndSavesAction() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.PENDING);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));
        when(reportRepository.save(any())).thenReturn(report);
        when(moderationActionRepository.save(any())).thenReturn(savedAction(report, mod, ActionType.REJECT));

        ModerationActionDTO.Response response =
                moderationService.reject(report.getReportId(), mod.getUserId(), note("Not valid"));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(response.getActionType()).isEqualTo(ActionType.REJECT);
    }

    @Test
    @DisplayName("reject: non-PENDING report → IllegalStateException")
    void reject_nonPendingReport_throwsIllegalStateException() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.VERIFIED);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));

        assertThatThrownBy(() ->
                moderationService.reject(report.getReportId(), mod.getUserId(), note(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REJECT requires PENDING");
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolve: VERIFIED report → status set to RESOLVED, action saved")
    void resolve_verifiedReport_setsResolvedAndSavesAction() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.VERIFIED);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));
        when(reportRepository.save(any())).thenReturn(report);
        when(moderationActionRepository.save(any())).thenReturn(savedAction(report, mod, ActionType.RESOLVE));

        ModerationActionDTO.Response response =
                moderationService.resolve(report.getReportId(), mod.getUserId(), note("Fixed"));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.getActionType()).isEqualTo(ActionType.RESOLVE);
    }

    @Test
    @DisplayName("resolve: PENDING report (not yet VERIFIED) → IllegalStateException")
    void resolve_pendingReport_throwsIllegalStateException() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.PENDING);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));

        assertThatThrownBy(() ->
                moderationService.resolve(report.getReportId(), mod.getUserId(), note(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESOLVE requires VERIFIED");
    }

    // ── expire ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("expire: PENDING report → status set to EXPIRED")
    void expire_pendingReport_setsExpired() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.PENDING);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));
        when(reportRepository.save(any())).thenReturn(report);
        when(moderationActionRepository.save(any())).thenReturn(savedAction(report, mod, ActionType.EXPIRE));

        moderationService.expire(report.getReportId(), mod.getUserId());

        assertThat(report.getStatus()).isEqualTo(ReportStatus.EXPIRED);
    }

    @Test
    @DisplayName("expire: VERIFIED report → status set to EXPIRED")
    void expire_verifiedReport_setsExpired() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.VERIFIED);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));
        when(reportRepository.save(any())).thenReturn(report);
        when(moderationActionRepository.save(any())).thenReturn(savedAction(report, mod, ActionType.EXPIRE));

        moderationService.expire(report.getReportId(), mod.getUserId());

        assertThat(report.getStatus()).isEqualTo(ReportStatus.EXPIRED);
    }

    @Test
    @DisplayName("expire: already REJECTED report → IllegalStateException")
    void expire_rejectedReport_throwsIllegalStateException() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.REJECTED);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));

        assertThatThrownBy(() ->
                moderationService.expire(report.getReportId(), mod.getUserId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRE requires PENDING or VERIFIED");
    }

    // ── access control ────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify: non-MODERATOR user → UnauthorizedException")
    void verify_nonModerator_throwsUnauthorizedException() {
        User reporter = buildReporter(); // REPORTER, not MODERATOR
        Report report = buildReport(buildReporter(), ReportStatus.PENDING);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() ->
                moderationService.verify(report.getReportId(), reporter.getUserId(), note(null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Only moderators");
    }

    @Test
    @DisplayName("verify: moderator acting on own report → UnauthorizedException")
    void verify_moderatorActingOnOwnReport_throwsUnauthorizedException() {
        User mod = buildModerator();
        // Report submitted by the moderator themselves
        Report report = buildReport(mod, ReportStatus.PENDING);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));

        assertThatThrownBy(() ->
                moderationService.verify(report.getReportId(), mod.getUserId(), note(null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("own report");
    }

    @Test
    @DisplayName("verify: unknown report ID → ResourceNotFoundException")
    void verify_unknownReport_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(reportRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                moderationService.verify(unknownId, UUID.randomUUID(), note(null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getPendingQueue ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPendingQueue: pending reports exist → returns mapped list")
    void getPendingQueue_pendingReportsExist_returnsList() {
        User reporter = buildReporter();
        Report r1 = buildReport(reporter, ReportStatus.PENDING);
        Report r2 = buildReport(reporter, ReportStatus.PENDING);

        when(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING))
                .thenReturn(java.util.List.of(r1, r2));

        java.util.List<com.accesspath.dto.ReportDTO.Response> queue =
                moderationService.getPendingQueue();

        assertThat(queue).hasSize(2);
        assertThat(queue).allMatch(r -> r.getStatus() == ReportStatus.PENDING);
    }

    @Test
    @DisplayName("getPendingQueue: no pending reports → returns empty list")
    void getPendingQueue_emptyQueue_returnsEmptyList() {
        when(reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING))
                .thenReturn(java.util.List.of());

        java.util.List<com.accesspath.dto.ReportDTO.Response> queue =
                moderationService.getPendingQueue();

        assertThat(queue).isEmpty();
    }

    // ── expire: RESOLVED report (terminal) ───────────────────────────────────

    @Test
    @DisplayName("expire: already RESOLVED report → IllegalStateException")
    void expire_resolvedReport_throwsIllegalStateException() {
        User mod = buildModerator();
        User reporter = buildReporter();
        Report report = buildReport(reporter, ReportStatus.RESOLVED);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(mod.getUserId())).thenReturn(Optional.of(mod));

        assertThatThrownBy(() ->
                moderationService.expire(report.getReportId(), mod.getUserId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRE requires PENDING or VERIFIED");
    }
}
