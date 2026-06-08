package com.accesspath.services;

import com.accesspath.dto.ReportDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.ReportRepository;
import com.accesspath.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService.
 * Covers US-3 (view reports), US-4 (submit report), US-5 (AI draft).
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClassificationService classificationService;
    @Mock private ConfidenceService confidenceService;

    @InjectMocks
    private ReportService reportService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildReporter() {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("reporter@test.com")
                .passwordHash("hash")
                .role(UserRole.REPORTER)
                .build();
    }

    private ReportDTO.CreateRequest buildRequest() {
        ReportDTO.CreateRequest req = new ReportDTO.CreateRequest();
        req.setDescription("Broken tactile paving near bus stop");
        req.setLatitude(25.2048);
        req.setLongitude(55.2708);
        req.setCategory("SURFACE_HAZARD");
        req.setSeverity("MEDIUM");
        return req;
    }

    // ── draftReport ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("draftReport: description processed → returns AI suggestions")
    void draftReport_validRequest_returnsAiSuggestions() {
        when(classificationService.suggestCategory(any())).thenReturn("SURFACE_HAZARD");
        when(classificationService.suggestSeverity(any())).thenReturn("MEDIUM");
        when(classificationService.getConfidenceFlag(any())).thenReturn(true);

        ReportDTO.DraftResponse response = reportService.draftReport(buildRequest());

        assertThat(response.getSuggestedCategory()).isEqualTo("SURFACE_HAZARD");
        assertThat(response.getSuggestedSeverity()).isEqualTo("MEDIUM");
        assertThat(response.isConfidenceFlag()).isTrue();
        assertThat(response.getLatitude()).isEqualTo(25.2048);
        assertThat(response.getLongitude()).isEqualTo(55.2708);
    }

    // ── createReport ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createReport: no duplicate nearby → saves and returns report")
    void createReport_noDuplicate_savesReport() {
        User reporter = buildReporter();
        ReportDTO.CreateRequest req = buildRequest();

        Report saved = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .description(req.getDescription())
                .confidenceScore(0.1f)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(604800))
                .build();

        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));
        when(reportRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportDTO.Response response = reportService.createReport(req, reporter.getUserId());

        assertThat(response.getCategory()).isEqualTo("SURFACE_HAZARD");
        assertThat(response.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    @DisplayName("createReport: duplicate active report within 50m → IllegalStateException")
    void createReport_duplicateNearby_throwsIllegalStateException() {
        User reporter = buildReporter();
        ReportDTO.CreateRequest req = buildRequest();

        Report existing = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .build();

        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));
        when(reportRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> reportService.createReport(req, reporter.getUserId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("similar report");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReport: unknown reporter ID → ResourceNotFoundException")
    void createReport_unknownUser_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.createReport(buildRequest(), unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getReportById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getReportById: valid ID → returns report DTO")
    void getReportById_validId_returnsReport() {
        User reporter = buildReporter();
        UUID reportId = UUID.randomUUID();
        Report report = Report.builder()
                .reportId(reportId)
                .reporter(reporter)
                .status(ReportStatus.VERIFIED)
                .category("STEP_FREE_ISSUE")
                .severity("HIGH")
                .description("Elevator broken")
                .confidenceScore(0.85f)
                .createdAt(Instant.now())
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        ReportDTO.Response response = reportService.getReportById(reportId);

        assertThat(response.getReportId()).isEqualTo(reportId);
        assertThat(response.getCategory()).isEqualTo("STEP_FREE_ISSUE");
        assertThat(response.getStatus()).isEqualTo(ReportStatus.VERIFIED);
    }

    @Test
    @DisplayName("getReportById: unknown ID → ResourceNotFoundException")
    void getReportById_unknownId_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(reportRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReportById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findNearby ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findNearby: returns mapped NearbyResponse list")
    void findNearby_returnsNearbyReports() {
        User reporter = buildReporter();
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("OBSTRUCTION")
                .severity("MEDIUM")
                .confidenceScore(0.6f)
                .createdAt(Instant.now())
                .build();

        when(reportRepository.findNearby(25.2048, 55.2708, 2000.0))
                .thenReturn(List.of(report));

        List<ReportDTO.NearbyResponse> results =
                reportService.findNearby(25.2048, 55.2708, 2000.0);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategory()).isEqualTo("OBSTRUCTION");
    }

    @Test
    @DisplayName("findNearby: no reports in area → empty list")
    void findNearby_noReports_returnsEmptyList() {
        when(reportRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        List<ReportDTO.NearbyResponse> results =
                reportService.findNearby(0.0, 0.0, 100.0);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findNearbyDefault: delegates to findNearby with DEFAULT_NEARBY_RADIUS of 100m")
    void findNearbyDefault_usesDefaultRadius() {
        when(reportRepository.findNearby(25.2048, 55.2708, 100.0))
                .thenReturn(Collections.emptyList());

        List<ReportDTO.NearbyResponse> results =
                reportService.findNearbyDefault(25.2048, 55.2708);

        assertThat(results).isEmpty();
        verify(reportRepository).findNearby(25.2048, 55.2708, 100.0);
    }

    // ── createReport edge cases ───────────────────────────────────────────────

    @Test
    @DisplayName("createReport: no category in request → AI suggestion used")
    void createReport_noCategoryInRequest_usesAiSuggestion() {
        User reporter = buildReporter();
        ReportDTO.CreateRequest req = new ReportDTO.CreateRequest();
        req.setDescription("Broken tactile paving near bus stop");
        req.setLatitude(25.2048);
        req.setLongitude(55.2708);
        // category and severity intentionally left null

        Report saved = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("SURFACE_HAZARD") // comes from AI
                .severity("MEDIUM")         // comes from AI
                .description(req.getDescription())
                .confidenceScore(0.1f)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));
        when(reportRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(classificationService.suggestCategory(req.getDescription())).thenReturn("SURFACE_HAZARD");
        when(classificationService.suggestSeverity(req.getDescription())).thenReturn("MEDIUM");
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportDTO.Response response = reportService.createReport(req, reporter.getUserId());

        assertThat(response.getCategory()).isEqualTo("SURFACE_HAZARD");
        verify(classificationService).suggestCategory(req.getDescription());
        verify(classificationService).suggestSeverity(req.getDescription());
    }

    @Test
    @DisplayName("createReport: nearby report is EXPIRED (same category) → allowed, saves report")
    void createReport_nearbyExpiredReportSameCategory_savesReport() {
        User reporter = buildReporter();
        ReportDTO.CreateRequest req = buildRequest();

        // Existing report at same location, same category, but EXPIRED — should NOT block
        Report expiredReport = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.EXPIRED)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .build();

        Report saved = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .description(req.getDescription())
                .confidenceScore(0.1f)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));
        when(reportRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(expiredReport));
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportDTO.Response response = reportService.createReport(req, reporter.getUserId());

        assertThat(response.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    @DisplayName("createReport: nearby PENDING report but different category → allowed")
    void createReport_nearbyReportDifferentCategory_savesReport() {
        User reporter = buildReporter();
        ReportDTO.CreateRequest req = buildRequest(); // category = SURFACE_HAZARD

        // Existing PENDING report nearby but different category
        Report otherCategory = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("OBSTRUCTION") // different category
                .severity("LOW")
                .build();

        Report saved = Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.PENDING)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .description(req.getDescription())
                .confidenceScore(0.1f)
                .createdAt(Instant.now())
                .build();

        when(userRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));
        when(reportRepository.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(otherCategory));
        when(reportRepository.save(any(Report.class))).thenReturn(saved);

        ReportDTO.Response response = reportService.createReport(req, reporter.getUserId());

        assertThat(response.getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(reportRepository).save(any(Report.class));
    }
}
