package com.accesspath.services;

import com.accesspath.dto.VoteDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.models.Vote;
import com.accesspath.models.Vote.VoteType;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.UserRepository;
import com.accesspath.repositories.VoteRepository;
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
 * Unit tests for VerificationService.
 * Covers US-6: voting (CONFIRM / DISPUTE) on reports.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock private VoteRepository voteRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConfidenceService confidenceService;

    @InjectMocks
    private VerificationService verificationService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildReporter() {
        return User.builder()
                .userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("reporter@test.com")
                .passwordHash("hash")
                .role(UserRole.REPORTER)
                .build();
    }

    private User buildVoter() {
        return User.builder()
                .userId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .email("voter@test.com")
                .passwordHash("hash")
                .role(UserRole.NAVIGATOR)
                .build();
    }

    private Report buildPendingReport(User reporter) {
        return Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("OBSTRUCTION")
                .severity("MEDIUM")
                .build();
    }

    // ── castVote ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("castVote: CONFIRM on pending report by different user → saves vote")
    void castVote_confirmByDifferentUser_savesVote() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = buildPendingReport(reporter);

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        Vote savedVote = Vote.builder()
                .voteId(UUID.randomUUID())
                .report(report)
                .user(voter)
                .voteType(VoteType.CONFIRM)
                .createdAt(Instant.now())
                .build();

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(false);
        when(voteRepository.save(any(Vote.class))).thenReturn(savedVote);

        VoteDTO.Response response = verificationService.castVote(
                report.getReportId(), voter.getUserId(), request);

        assertThat(response.getVoteType()).isEqualTo(VoteType.CONFIRM);
        verify(voteRepository).save(any(Vote.class));
        verify(confidenceService).refreshCues(report.getReportId());
    }

    @Test
    @DisplayName("castVote: DISPUTE on pending report → saves vote")
    void castVote_disputeByDifferentUser_savesVote() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = buildPendingReport(reporter);

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.DISPUTE);

        Vote savedVote = Vote.builder()
                .voteId(UUID.randomUUID())
                .report(report)
                .user(voter)
                .voteType(VoteType.DISPUTE)
                .createdAt(Instant.now())
                .build();

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(false);
        when(voteRepository.save(any(Vote.class))).thenReturn(savedVote);

        VoteDTO.Response response = verificationService.castVote(
                report.getReportId(), voter.getUserId(), request);

        assertThat(response.getVoteType()).isEqualTo(VoteType.DISPUTE);
    }

    @Test
    @DisplayName("castVote: user votes on their own report → UnauthorizedException")
    void castVote_selfVote_throwsUnauthorizedException() {
        User reporter = buildReporter();
        Report report = buildPendingReport(reporter);

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() ->
                verificationService.castVote(report.getReportId(), reporter.getUserId(), request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("own report");

        verify(voteRepository, never()).save(any());
    }

    @Test
    @DisplayName("castVote: duplicate vote → IllegalStateException")
    void castVote_duplicateVote_throwsIllegalStateException() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = buildPendingReport(reporter);

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(true);

        assertThatThrownBy(() ->
                verificationService.castVote(report.getReportId(), voter.getUserId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already voted");

        verify(voteRepository, never()).save(any());
    }

    @Test
    @DisplayName("castVote: vote on REJECTED report → IllegalStateException")
    void castVote_rejectedReport_throwsIllegalStateException() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.REJECTED)
                .category("OBSTRUCTION")
                .severity("LOW")
                .build();

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                verificationService.castVote(report.getReportId(), voter.getUserId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    @DisplayName("castVote: vote on RESOLVED report → IllegalStateException")
    void castVote_resolvedReport_throwsIllegalStateException() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.RESOLVED)
                .category("OBSTRUCTION")
                .severity("LOW")
                .build();

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                verificationService.castVote(report.getReportId(), voter.getUserId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    @DisplayName("castVote: vote on EXPIRED report → IllegalStateException")
    void castVote_expiredReport_throwsIllegalStateException() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.EXPIRED)
                .category("OBSTRUCTION")
                .severity("LOW")
                .build();

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                verificationService.castVote(report.getReportId(), voter.getUserId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    @DisplayName("castVote: CONFIRM on VERIFIED report → saves vote (not blocked)")
    void castVote_confirmOnVerifiedReport_savesVote() {
        User reporter = buildReporter();
        User voter = buildVoter();
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.VERIFIED)
                .category("OBSTRUCTION")
                .severity("MEDIUM")
                .build();

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        Vote savedVote = Vote.builder()
                .voteId(UUID.randomUUID())
                .report(report)
                .user(voter)
                .voteType(VoteType.CONFIRM)
                .createdAt(Instant.now())
                .build();

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(voter.getUserId())).thenReturn(Optional.of(voter));
        when(voteRepository.existsByReport_ReportIdAndUser_UserId(any(), any())).thenReturn(false);
        when(voteRepository.save(any(Vote.class))).thenReturn(savedVote);

        VoteDTO.Response response = verificationService.castVote(
                report.getReportId(), voter.getUserId(), request);

        assertThat(response.getVoteType()).isEqualTo(VoteType.CONFIRM);
        verify(voteRepository).save(any(Vote.class));
    }

    @Test
    @DisplayName("castVote: unknown user ID → ResourceNotFoundException")
    void castVote_unknownUser_throwsResourceNotFoundException() {
        User reporter = buildReporter();
        Report report = buildPendingReport(reporter);
        UUID unknownUserId = UUID.randomUUID();

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                verificationService.castVote(report.getReportId(), unknownUserId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("castVote: unknown report ID → ResourceNotFoundException")
    void castVote_unknownReport_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(reportRepository.findById(unknownId)).thenReturn(Optional.empty());

        VoteDTO.CreateRequest request = new VoteDTO.CreateRequest();
        request.setVoteType(VoteType.CONFIRM);

        assertThatThrownBy(() ->
                verificationService.castVote(unknownId, UUID.randomUUID(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getConfirmCount / getDisputeCount ─────────────────────────────────────

    @Test
    @DisplayName("getConfirmCount: returns repository CONFIRM vote count for report")
    void getConfirmCount_returnsConfirmVoteCount() {
        UUID reportId = UUID.randomUUID();
        when(voteRepository.countByReport_ReportIdAndVoteType(reportId, VoteType.CONFIRM))
                .thenReturn(3L);

        long count = verificationService.getConfirmCount(reportId);

        assertThat(count).isEqualTo(3L);
        verify(voteRepository).countByReport_ReportIdAndVoteType(reportId, VoteType.CONFIRM);
    }

    @Test
    @DisplayName("getDisputeCount: returns repository DISPUTE vote count for report")
    void getDisputeCount_returnsDisputeVoteCount() {
        UUID reportId = UUID.randomUUID();
        when(voteRepository.countByReport_ReportIdAndVoteType(reportId, VoteType.DISPUTE))
                .thenReturn(2L);

        long count = verificationService.getDisputeCount(reportId);

        assertThat(count).isEqualTo(2L);
        verify(voteRepository).countByReport_ReportIdAndVoteType(reportId, VoteType.DISPUTE);
    }
}
