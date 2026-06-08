package com.accesspath.services;

import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.Vote.VoteType;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.VoteRepository;
import com.accesspath.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfidenceService {

    private final ReportRepository reportRepository;
    private final VoteRepository voteRepository;

    // Weights for confidence score calculation
    private static final float VERIFIED_BONUS = 0.4f;
    private static final float CONFIRM_WEIGHT = 0.1f;
    private static final float DISPUTE_PENALTY = 0.15f;
    private static final float MAX_VOTE_CONTRIBUTION = 0.4f;
    private static final float FRESHNESS_WEIGHT = 0.2f;

    @Transactional
    public float calculateScore(Report report) {
        float score = 0.0f;

        // 1. Status bonus
        if (report.getStatus() == ReportStatus.VERIFIED) {
            score += VERIFIED_BONUS;
        } else if (report.getStatus() == ReportStatus.PENDING) {
            score += 0.1f;
        }

        // 2. Vote contribution
        long confirms = voteRepository.countByReport_ReportIdAndVoteType(
            report.getReportId(), VoteType.CONFIRM
        );
        long disputes = voteRepository.countByReport_ReportIdAndVoteType(
            report.getReportId(), VoteType.DISPUTE
        );

        float voteScore = (confirms * CONFIRM_WEIGHT)
            - (disputes * DISPUTE_PENALTY);
        voteScore = Math.max(0, Math.min(voteScore, MAX_VOTE_CONTRIBUTION));
        score += voteScore;

        // 3. Freshness contribution
        float freshness = calculateFreshness(report.getCreatedAt());
        score += freshness * FRESHNESS_WEIGHT;

        // Clamp between 0 and 1
        score = Math.max(0.0f, Math.min(1.0f, score));

        log.debug("Confidence score for report {}: {}",
            report.getReportId(), score);
        return score;
    }

    @Transactional
    public void refreshCues(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Report", reportId.toString()
                ));

        float newScore = calculateScore(report);
        report.setConfidenceScore(newScore);
        reportRepository.save(report);

        log.info("Refreshed confidence score for report {}: {}",
            reportId, newScore);
    }

    private float calculateFreshness(Instant createdAt) {
        long hoursOld = ChronoUnit.HOURS.between(createdAt, Instant.now());

        if (hoursOld < 24) return 1.0f;
        if (hoursOld < 72) return 0.7f;
        if (hoursOld < 168) return 0.4f;
        return 0.1f;
    }
}