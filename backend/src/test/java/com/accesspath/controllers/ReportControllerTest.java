package com.accesspath.controllers;

import com.accesspath.dto.ReportDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.security.JwtUtil;
import com.accesspath.security.UserDetailsServiceImpl;
import com.accesspath.services.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer unit tests for ReportController.
 * Covers US-3 (view reports), US-4 (submit report), US-5 (AI draft) HTTP layer.
 * Security filters are disabled — service logic is covered by ReportServiceTest.
 */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private static final String VALID_BODY =
            "{\"description\":\"Broken tactile paving near bus stop\"," +
            "\"latitude\":25.2048,\"longitude\":55.2708," +
            "\"category\":\"SURFACE_HAZARD\",\"severity\":\"MEDIUM\"}";

    // ── POST /api/reports/draft ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/reports/draft: valid request → 200 with AI suggestions")
    void draftReport_validRequest_returns200WithSuggestions() throws Exception {
        ReportDTO.DraftResponse draft = ReportDTO.DraftResponse.builder()
                .suggestedCategory("SURFACE_HAZARD")
                .suggestedSeverity("MEDIUM")
                .confidenceFlag(true)
                .latitude(25.2048)
                .longitude(55.2708)
                .description("Broken tactile paving near bus stop")
                .build();

        when(reportService.draftReport(any(ReportDTO.CreateRequest.class))).thenReturn(draft);

        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCategory").value("SURFACE_HAZARD"))
                .andExpect(jsonPath("$.suggestedSeverity").value("MEDIUM"))
                .andExpect(jsonPath("$.confidenceFlag").value(true));
    }

    @Test
    @DisplayName("POST /api/reports/draft: missing description → 400 Bad Request")
    void draftReport_missingDescription_returns400() throws Exception {
        String body = "{\"latitude\":25.2048,\"longitude\":55.2708}";

        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // ── POST /api/reports ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/reports: valid request with token → 201 with report")
    void createReport_validRequest_returns201() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReportDTO.Response response = ReportDTO.Response.builder()
                .reportId(reportId)
                .reporterUserId(userId)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .description("Broken tactile paving near bus stop")
                .latitude(25.2048)
                .longitude(55.2708)
                .status(ReportStatus.PENDING)
                .confidenceScore(0.1f)
                .createdAt(Instant.now())
                .build();

        when(jwtUtil.extractUserId(any())).thenReturn(userId);
        when(reportService.createReport(any(ReportDTO.CreateRequest.class), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SURFACE_HAZARD"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/reports: duplicate nearby report → 409 Conflict")
    void createReport_duplicateNearby_returns409() throws Exception {
        UUID userId = UUID.randomUUID();

        when(jwtUtil.extractUserId(any())).thenReturn(userId);
        when(reportService.createReport(any(), any())).thenThrow(
                new IllegalStateException("A similar report already exists nearby"));

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── GET /api/reports/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/reports/{id}: valid ID → 200 with report")
    void getReport_validId_returns200() throws Exception {
        UUID reportId = UUID.randomUUID();

        ReportDTO.Response response = ReportDTO.Response.builder()
                .reportId(reportId)
                .category("STEP_FREE_ISSUE")
                .severity("HIGH")
                .status(ReportStatus.VERIFIED)
                .confidenceScore(0.85f)
                .createdAt(Instant.now())
                .build();

        when(reportService.getReportById(reportId)).thenReturn(response);

        mockMvc.perform(get("/api/reports/{id}", reportId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.category").value("STEP_FREE_ISSUE"))
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("GET /api/reports/{id}: unknown ID → 404 Not Found")
    void getReport_unknownId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();

        when(reportService.getReportById(unknownId))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(get("/api/reports/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/reports/nearby ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/reports/nearby: valid params → 200 with list")
    void getNearby_validParams_returns200() throws Exception {
        ReportDTO.NearbyResponse nearby = ReportDTO.NearbyResponse.builder()
                .reportId(UUID.randomUUID())
                .category("OBSTRUCTION")
                .severity("MEDIUM")
                .status(ReportStatus.PENDING)
                .latitude(25.2048)
                .longitude(55.2708)
                .confidenceScore(0.6f)
                .createdAt(Instant.now())
                .build();

        when(reportService.findNearby(25.2048, 55.2708, 500.0))
                .thenReturn(List.of(nearby));

        mockMvc.perform(get("/api/reports/nearby")
                        .param("lat", "25.2048")
                        .param("lng", "55.2708")
                        .param("radius", "500.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].category").value("OBSTRUCTION"));
    }

    @Test
    @DisplayName("GET /api/reports/nearby: no reports → 200 with empty array")
    void getNearby_noReports_returnsEmptyArray() throws Exception {
        when(reportService.findNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/nearby")
                        .param("lat", "0.0")
                        .param("lng", "0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("POST /api/reports: missing description → 400 Bad Request")
    void createReport_missingDescription_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"latitude\":25.2048,\"longitude\":55.2708,\"category\":\"SURFACE_HAZARD\",\"severity\":\"MEDIUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // ── coordinate boundary validation: POST /api/reports ────────────────────

    @Test
    @DisplayName("POST /api/reports: latitude exactly 90.0 (boundary) → 201 Created")
    void createReport_latitudeExactly90_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.extractUserId(any())).thenReturn(userId);
        when(reportService.createReport(any(), any())).thenReturn(
                ReportDTO.Response.builder().reportId(UUID.randomUUID())
                        .category("SURFACE_HAZARD").severity("MEDIUM")
                        .status(ReportStatus.PENDING).createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":90.0,\"longitude\":55.2708,\"category\":\"SURFACE_HAZARD\",\"severity\":\"MEDIUM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/reports: latitude exactly -90.0 (boundary) → 201 Created")
    void createReport_latitudeExactlyMinus90_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.extractUserId(any())).thenReturn(userId);
        when(reportService.createReport(any(), any())).thenReturn(
                ReportDTO.Response.builder().reportId(UUID.randomUUID())
                        .category("SURFACE_HAZARD").severity("MEDIUM")
                        .status(ReportStatus.PENDING).createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":-90.0,\"longitude\":55.2708,\"category\":\"SURFACE_HAZARD\",\"severity\":\"MEDIUM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/reports: longitude exactly 180.0 (boundary) → 201 Created")
    void createReport_longitudeExactly180_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.extractUserId(any())).thenReturn(userId);
        when(reportService.createReport(any(), any())).thenReturn(
                ReportDTO.Response.builder().reportId(UUID.randomUUID())
                        .category("SURFACE_HAZARD").severity("MEDIUM")
                        .status(ReportStatus.PENDING).createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":25.2048,\"longitude\":180.0,\"category\":\"SURFACE_HAZARD\",\"severity\":\"MEDIUM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/reports: longitude exactly -180.0 (boundary) → 201 Created")
    void createReport_longitudeExactlyMinus180_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.extractUserId(any())).thenReturn(userId);
        when(reportService.createReport(any(), any())).thenReturn(
                ReportDTO.Response.builder().reportId(UUID.randomUUID())
                        .category("SURFACE_HAZARD").severity("MEDIUM")
                        .status(ReportStatus.PENDING).createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":25.2048,\"longitude\":-180.0,\"category\":\"SURFACE_HAZARD\",\"severity\":\"MEDIUM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/reports: missing latitude → 400 Bad Request")
    void createReport_missingLatitude_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/reports: missing longitude → 400 Bad Request")
    void createReport_missingLongitude_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":25.2048}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/reports: latitude just above 90 (90.0001) → 400 Bad Request")
    void createReport_latitudeJustAbove90_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":90.0001,\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports: latitude just below -90 (-90.0001) → 400 Bad Request")
    void createReport_latitudeJustBelow90_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":-90.0001,\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports: longitude just above 180 (180.0001) → 400 Bad Request")
    void createReport_longitudeJustAbove180_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":25.2048,\"longitude\":180.0001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports: longitude just below -180 (-180.0001) → 400 Bad Request")
    void createReport_longitudeJustBelow180_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":25.2048,\"longitude\":-180.0001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports: far out-of-range latitude (200) → 400 Bad Request")
    void createReport_latitudeFarOutOfRange_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":200.0,\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports: far out-of-range longitude (360) → 400 Bad Request")
    void createReport_longitudeFarOutOfRange_returns400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"description\":\"test\",\"latitude\":25.2048,\"longitude\":360.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports: non-Bearer auth header → 401 Unauthorized")
    void createReport_nonBearerAuthHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    // ── coordinate boundary validation: POST /api/reports/draft ─────────────

    @Test
    @DisplayName("POST /api/reports/draft: missing latitude → 400 Bad Request")
    void draftReport_missingLatitude_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Broken ramp\",\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/reports/draft: missing longitude → 400 Bad Request")
    void draftReport_missingLongitude_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Broken ramp\",\"latitude\":25.2048}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/reports/draft: latitude just above 90 (91.0) → 400 Bad Request")
    void draftReport_latitudeAbove90_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Broken ramp\",\"latitude\":91.0,\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports/draft: latitude just below -90 (-91.0) → 400 Bad Request")
    void draftReport_latitudeBelow90_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Broken ramp\",\"latitude\":-91.0,\"longitude\":55.2708}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports/draft: longitude just above 180 (181.0) → 400 Bad Request")
    void draftReport_longitudeAbove180_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Broken ramp\",\"latitude\":25.2048,\"longitude\":181.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/reports/draft: longitude just below -180 (-181.0) → 400 Bad Request")
    void draftReport_longitudeBelow180_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Broken ramp\",\"latitude\":25.2048,\"longitude\":-181.0}"))
                .andExpect(status().isBadRequest());
    }
}
