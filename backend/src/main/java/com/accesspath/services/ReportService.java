package com.accesspath.services;

import com.accesspath.dto.ReportDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.User;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ClassificationService classificationService;
    private final ConfidenceService confidenceService;

    private static final GeometryFactory GEOMETRY_FACTORY =
        new GeometryFactory(new PrecisionModel(), 4326);

    private static final double DEFAULT_NEARBY_RADIUS = 100.0;
    private static final int TTL_DAYS = 7;

    // Step 1 — Draft report with AI suggestion
    public ReportDTO.DraftResponse draftReport(
            ReportDTO.CreateRequest request) {

        String suggestedCategory = classificationService
            .suggestCategory(request.getDescription());
        String suggestedSeverity = classificationService
            .suggestSeverity(request.getDescription());
        boolean confidenceFlag = classificationService
            .getConfidenceFlag(request.getDescription());

        return ReportDTO.DraftResponse.builder()
                .suggestedCategory(suggestedCategory)
                .suggestedSeverity(suggestedSeverity)
                .confidenceFlag(confidenceFlag)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .description(request.getDescription())
                .build();
    }

    // Step 2 — Create final report after user confirms
    @Transactional
    public ReportDTO.Response createReport(
            ReportDTO.CreateRequest request,
            UUID reporterUserId) {

        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "User", reporterUserId.toString()
                ));

        // Use AI suggestion if user didn't override
        String category = (request.getCategory() != null)
            ? request.getCategory()
            : classificationService.suggestCategory(request.getDescription());

        String severity = (request.getSeverity() != null)
            ? request.getSeverity()
            : classificationService.suggestSeverity(request.getDescription());

        // Reject if an active report of the same category exists within 50 m
        boolean duplicate = reportRepository
            .findNearby(request.getLatitude(), request.getLongitude(), 50.0)
            .stream()
            .anyMatch(r -> r.getCategory().equals(category)
                && (r.getStatus() == ReportStatus.PENDING
                    || r.getStatus() == ReportStatus.VERIFIED));
        if (duplicate) {
            throw new IllegalStateException(
                "A similar report already exists within 50 m of this location");
        }

        // Build PostGIS point
        Point location = GEOMETRY_FACTORY.createPoint(
            new Coordinate(request.getLongitude(), request.getLatitude())
        );

        Report report = Report.builder()
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category(category)
                .severity(severity)
                .description(request.getDescription())
                .locationPoint(location)
                .confidenceScore(0.1f)
                .expiresAt(Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS))
                .build();

        Report saved = reportRepository.save(report);
        log.info("Created report: {} by user: {}",
            saved.getReportId(), reporterUserId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReportDTO.Response getReportById(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Report", reportId.toString()
                ));
        return mapToResponse(report);
    }

    @Transactional(readOnly = true)
    public List<ReportDTO.NearbyResponse> findNearby(
            double lat, double lng, double radiusMeters) {

        List<Report> reports = reportRepository.findNearby(
            lat, lng, radiusMeters
        );

        return reports.stream()
                .map(this::mapToNearbyResponse)
                .collect(Collectors.toList());
    }

    public List<ReportDTO.NearbyResponse> findNearbyDefault(
            double lat, double lng) {
        return findNearby(lat, lng, DEFAULT_NEARBY_RADIUS);
    }

    // Mapping helpers
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

    private ReportDTO.NearbyResponse mapToNearbyResponse(Report report) {
        double lat = 0.0;
        double lng = 0.0;

        if (report.getLocationPoint() != null) {
            lat = report.getLocationPoint().getY();
            lng = report.getLocationPoint().getX();
        }

        return ReportDTO.NearbyResponse.builder()
                .reportId(report.getReportId())
                .category(report.getCategory())
                .severity(report.getSeverity())
                .status(report.getStatus())
                .latitude(lat)
                .longitude(lng)
                .confidenceScore(report.getConfidenceScore())
                .createdAt(report.getCreatedAt())
                .build();
    }
}