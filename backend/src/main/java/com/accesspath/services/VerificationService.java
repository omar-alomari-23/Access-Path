package com.accesspath.services;

import com.accesspath.dto.VoteDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.models.Report;
import com.accesspath.models.User;
import com.accesspath.models.Vote;
import com.accesspath.models.Vote.VoteType;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.UserRepository;
import com.accesspath.repositories.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final VoteRepository voteRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ConfidenceService confidenceService;

    @Transactional
    public VoteDTO.Response castVote(
            UUID reportId,
            UUID userId,
            VoteDTO.CreateRequest request) {

        // Check report exists
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Report", reportId.toString()
                ));

        // Check user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User", userId.toString()
                ));

        // Prevent voting on own report
        if (report.getReporter().getUserId().equals(userId)) {
            throw new UnauthorizedException(
                "Cannot vote on your own report"
            );
        }

        // Prevent duplicate voting
        if (voteRepository.existsByReport_ReportIdAndUser_UserId(
                reportId, userId)) {
            throw new IllegalStateException(
                "User has already voted on this report"
            );
        }

        // Only allow voting on PENDING or VERIFIED reports
        if (report.getStatus() == Report.ReportStatus.REJECTED ||
            report.getStatus() == Report.ReportStatus.RESOLVED ||
            report.getStatus() == Report.ReportStatus.EXPIRED) {
            throw new IllegalStateException(
                "Cannot vote on report in terminal state: "
                + report.getStatus()
            );
        }

        Vote vote = Vote.builder()
                .report(report)
                .user(user)
                .voteType(request.getVoteType())
                .build();

        Vote saved = voteRepository.save(vote);
        log.info("Vote cast: {} on report {} by user {}",
            request.getVoteType(), reportId, userId);

        // Refresh confidence score after vote
        confidenceService.refreshCues(reportId);

        return VoteDTO.Response.builder()
                .voteId(saved.getVoteId())
                .reportId(reportId)
                .userId(userId)
                .voteType(saved.getVoteType())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public long getConfirmCount(UUID reportId) {
        return voteRepository.countByReport_ReportIdAndVoteType(
            reportId, VoteType.CONFIRM
        );
    }

    public long getDisputeCount(UUID reportId) {
        return voteRepository.countByReport_ReportIdAndVoteType(
            reportId, VoteType.DISPUTE
        );
    }
}