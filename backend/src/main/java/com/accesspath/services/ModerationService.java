package com.accesspath.services;

import com.accesspath.dto.ModerationActionDTO;
import com.accesspath.dto.ReportDTO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ConfidenceService confidenceService;

    @Transactional(readOnly = true)
    public List<ReportDTO.Response> getPendingQueue() {
        return reportRepository
            .findByStatusOrderByCreatedAtDesc(ReportStatus.PENDING)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public ModerationActionDTO.Response verify(
            UUID reportId,
            UUID moderatorId,
            ModerationActionDTO.ActionRequest request) {
        return performAction(
            reportId, moderatorId,
            ReportStatus.VERIFIED,
            ActionType.VERIFY,
            request.getNote()
        );
    }

    @Transactional
    public ModerationActionDTO.Response reject(
            UUID reportId,
            UUID moderatorId,
            ModerationActionDTO.ActionRequest request) {
        return performAction(
            reportId, moderatorId,
            ReportStatus.REJECTED,
            ActionType.REJECT,
            request.getNote()
        );
    }

    @Transactional
    public ModerationActionDTO.Response resolve(
            UUID reportId,
            UUID moderatorId,
            ModerationActionDTO.ActionRequest request) {
        return performAction(
            reportId, moderatorId,
            ReportStatus.RESOLVED,
            ActionType.RESOLVE,
            request.getNote()
        );
    }

    @Transactional
    public ModerationActionDTO.Response expire(
            UUID reportId,
            UUID moderatorId) {
        return performAction(
            reportId, moderatorId,
            ReportStatus.EXPIRED,
            ActionType.EXPIRE,
            "Manually expired by moderator"
        );
    }

    private ModerationActionDTO.Response performAction(
            UUID reportId,
            UUID moderatorId,
            ReportStatus newStatus,
            ActionType actionType,
            String note) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Report", reportId.toString()
                ));

        User moderator = userRepository.findById(moderatorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User", moderatorId.toString()
                ));

        // Verify user is actually a moderator
        if (moderator.getRole() != UserRole.MODERATOR) {
            throw new UnauthorizedException(
                "Only moderators can perform this action"
            );
        }

        // Check moderator not acting on own report
        if (report.getReporter().getUserId().equals(moderatorId)) {
            throw new UnauthorizedException(
                "Moderator cannot act on their own report"
            );
        }

        // Per-action state machine validation
        switch (actionType) {
            case VERIFY -> {
                if (report.getStatus() != ReportStatus.PENDING)
                    throw new IllegalStateException(
                        "VERIFY requires PENDING status, found: " + report.getStatus());
            }
            case REJECT -> {
                if (report.getStatus() != ReportStatus.PENDING)
                    throw new IllegalStateException(
                        "REJECT requires PENDING status, found: " + report.getStatus());
            }
            case RESOLVE -> {
                if (report.getStatus() != ReportStatus.VERIFIED)
                    throw new IllegalStateException(
                        "RESOLVE requires VERIFIED status, found: " + report.getStatus());
            }
            case EXPIRE -> {
                if (report.getStatus() != ReportStatus.PENDING &&
                    report.getStatus() != ReportStatus.VERIFIED)
                    throw new IllegalStateException(
                        "EXPIRE requires PENDING or VERIFIED status, found: " + report.getStatus());
            }
            default -> throw new IllegalStateException("Unknown action type: " + actionType);
        }

        // Update report status
        report.setStatus(newStatus);
        reportRepository.save(report);

        // Log moderation action
        ModerationAction action = ModerationAction.builder()
                .report(report)
                .moderator(moderator)
                .actionType(actionType)
                .note(note)
                .build();

        ModerationAction saved = moderationActionRepository.save(action);
        log.info("Moderation action: {} on report {} by moderator {}",
            actionType, reportId, moderatorId);

        // Refresh confidence score after moderation
        confidenceService.refreshCues(reportId);

        return ModerationActionDTO.Response.builder()
                .actionId(saved.getActionId())
                .reportId(reportId)
                .moderatorUserId(moderatorId)
                .actionType(saved.getActionType())
                .note(saved.getNote())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private ReportDTO.Response mapToResponse(Report report) {
        double lat = 0.0;
        double lng = 0.0;

        if (report.getLocationPoint() != null) {
            lat = report.getLocationPoint().getY();
            lng = report.getLocationPoint().getX();
        }

        return ReportDTO.Response.builder()
                .reportId(report.getReportId())
                .reporterUserId(report.getReporter().getUserId())
                .category(report.getCategory())
                .severity(report.getSeverity())
                .description(report.getDescription())
                .latitude(lat)
                .longitude(lng)
                .status(report.getStatus())
                .confidenceScore(report.getConfidenceScore())
                .createdAt(report.getCreatedAt())
                .expiresAt(report.getExpiresAt())
                .build();
    }
}