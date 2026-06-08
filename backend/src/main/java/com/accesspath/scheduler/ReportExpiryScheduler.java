package com.accesspath.scheduler;

import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.services.ConfidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportExpiryScheduler {

    private final ReportRepository reportRepository;
    private final ConfidenceService confidenceService;

    // Runs every hour
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void expireStaleReports() {
        log.info("Running report expiry check at {}", Instant.now());

        List<Report> expiredReports = reportRepository
            .findByExpiresAtBeforeAndStatusNot(
                Instant.now(),
                ReportStatus.EXPIRED
            );

        if (expiredReports.isEmpty()) {
            log.info("No reports to expire");
            return;
        }

        int expiredCount = 0;
        for (Report report : expiredReports) {
            // Only expire PENDING or VERIFIED reports
            // REJECTED and RESOLVED stay as they are
            if (report.getStatus() == ReportStatus.PENDING ||
                report.getStatus() == ReportStatus.VERIFIED) {

                ReportStatus previousStatus = report.getStatus();
                report.setStatus(ReportStatus.EXPIRED);
                reportRepository.save(report);
                confidenceService.refreshCues(report.getReportId());
                expiredCount++;

                log.info("Expired report: {} (was: {})",
                    report.getReportId(),
                    previousStatus
                );
            }
        }

        log.info("Expiry check complete. Expired {} reports", expiredCount);
    }
}