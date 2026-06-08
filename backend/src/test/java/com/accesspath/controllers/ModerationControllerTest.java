package com.accesspath.controllers;

import com.accesspath.dto.ModerationActionDTO;
import com.accesspath.dto.ReportDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.models.ModerationAction.ActionType;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.security.JwtUtil;
import com.accesspath.security.UserDetailsServiceImpl;
import com.accesspath.services.ModerationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer unit tests for ModerationController.
 * Covers US-7 (moderation queue) and US-8 (moderation actions) HTTP layer.
 * Security filters are disabled — service logic is covered by ModerationServiceTest.
 */
@WebMvcTest(ModerationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModerationService moderationService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private static final UUID REPORT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID MOD_ID    = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private ModerationActionDTO.Response actionResponse(ActionType type) {
        return ModerationActionDTO.Response.builder()
                .actionId(UUID.randomUUID())
                .reportId(REPORT_ID)
                .moderatorUserId(MOD_ID)
                .actionType(type)
                .note("test note")
                .createdAt(Instant.now())
                .build();
    }

    // ── GET /api/moderation/queue ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/moderation/queue: returns 200 with pending report list")
    void getQueue_returns200WithList() throws Exception {
        ReportDTO.Response pending = ReportDTO.Response.builder()
                .reportId(REPORT_ID)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .status(ReportStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(moderationService.getPendingQueue()).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/moderation/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].category").value("SURFACE_HAZARD"));
    }

    @Test
    @DisplayName("GET /api/moderation/queue: empty queue → 200 with empty array")
    void getQueue_emptyQueue_returns200EmptyArray() throws Exception {
        when(moderationService.getPendingQueue()).thenReturn(List.of());

        mockMvc.perform(get("/api/moderation/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/moderation/{reportId}/verify ────────────────────────────────

    @Test
    @DisplayName("POST /verify: valid PENDING report → 200 with VERIFY action")
    void verify_pendingReport_returns200() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.verify(any(), any(), any()))
                .thenReturn(actionResponse(ActionType.VERIFY));

        mockMvc.perform(post("/api/moderation/{reportId}/verify", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"note\":\"Confirmed on site\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("VERIFY"));
    }

    @Test
    @DisplayName("POST /verify: already verified report → 409 Conflict")
    void verify_alreadyVerified_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.verify(any(), any(), any()))
                .thenThrow(new IllegalStateException("VERIFY requires PENDING status"));

        mockMvc.perform(post("/api/moderation/{reportId}/verify", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /verify: unknown report → 404 Not Found")
    void verify_unknownReport_returns404() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.verify(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(post("/api/moderation/{reportId}/verify", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /verify: moderator verifying own report → 401 Unauthorized")
    void verify_moderatorOwnReport_returns401() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.verify(any(), any(), any()))
                .thenThrow(new UnauthorizedException("Cannot moderate your own report"));

        mockMvc.perform(post("/api/moderation/{reportId}/verify", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /verify: non-moderator → 401 Unauthorized")
    void verify_nonModerator_returns401() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.verify(any(), any(), any()))
                .thenThrow(new UnauthorizedException("Only moderators can perform this action"));

        mockMvc.perform(post("/api/moderation/{reportId}/verify", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /verify: no request body (body is optional) → 200 with VERIFY action")
    void verify_noBody_returns200() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.verify(any(), any(), any()))
                .thenReturn(actionResponse(ActionType.VERIFY));

        mockMvc.perform(post("/api/moderation/{reportId}/verify", REPORT_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("VERIFY"));
    }

    // ── POST /api/moderation/{reportId}/reject ────────────────────────────────

    @Test
    @DisplayName("POST /reject: valid PENDING report → 200 with REJECT action")
    void reject_pendingReport_returns200() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.reject(any(), any(), any()))
                .thenReturn(actionResponse(ActionType.REJECT));

        mockMvc.perform(post("/api/moderation/{reportId}/reject", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"note\":\"Not valid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("REJECT"));
    }

    @Test
    @DisplayName("POST /reject: VERIFIED report → 409 Conflict (reject requires PENDING)")
    void reject_verifiedReport_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.reject(any(), any(), any()))
                .thenThrow(new IllegalStateException("REJECT requires PENDING status"));

        mockMvc.perform(post("/api/moderation/{reportId}/reject", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /reject: unknown report → 404 Not Found")
    void reject_unknownReport_returns404() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.reject(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(post("/api/moderation/{reportId}/reject", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── POST /api/moderation/{reportId}/resolve ───────────────────────────────

    @Test
    @DisplayName("POST /resolve: verified report → 200 with RESOLVE action")
    void resolve_verifiedReport_returns200() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.resolve(any(), any(), any()))
                .thenReturn(actionResponse(ActionType.RESOLVE));

        mockMvc.perform(post("/api/moderation/{reportId}/resolve", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"note\":\"Issue fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("RESOLVE"));
    }

    @Test
    @DisplayName("POST /resolve: EXPIRED report → 409 Conflict (resolve requires VERIFIED)")
    void resolve_expiredReport_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.resolve(any(), any(), any()))
                .thenThrow(new IllegalStateException("RESOLVE requires VERIFIED status"));

        mockMvc.perform(post("/api/moderation/{reportId}/resolve", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /resolve: non-verified report → 409 Conflict")
    void resolve_nonVerifiedReport_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.resolve(any(), any(), any()))
                .thenThrow(new IllegalStateException("RESOLVE requires VERIFIED status"));

        mockMvc.perform(post("/api/moderation/{reportId}/resolve", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── POST /api/moderation/{reportId}/expire ────────────────────────────────

    @Test
    @DisplayName("POST /expire: active report → 200 with EXPIRE action")
    void expire_activeReport_returns200() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.expire(any(), any()))
                .thenReturn(actionResponse(ActionType.EXPIRE));

        mockMvc.perform(post("/api/moderation/{reportId}/expire", REPORT_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("EXPIRE"));
    }

    @Test
    @DisplayName("POST /expire: already terminal report → 409 Conflict")
    void expire_terminalReport_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(MOD_ID);
        when(moderationService.expire(any(), any()))
                .thenThrow(new IllegalStateException("EXPIRE requires PENDING or VERIFIED status"));

        mockMvc.perform(post("/api/moderation/{reportId}/expire", REPORT_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
