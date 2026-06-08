package com.accesspath.controllers;

import com.accesspath.dto.VoteDTO;
import com.accesspath.exceptions.ResourceNotFoundException;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.models.Vote.VoteType;
import com.accesspath.security.JwtUtil;
import com.accesspath.security.UserDetailsServiceImpl;
import com.accesspath.services.VerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer unit tests for VoteController.
 * Covers US-6 (CONFIRM / DISPUTE voting) HTTP layer.
 * Security filters are disabled — service logic is covered by VerificationServiceTest.
 */
@WebMvcTest(VoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class VoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VerificationService verificationService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private static final UUID REPORT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VOTER_ID  = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    // ── POST /api/reports/{reportId}/votes ────────────────────────────────────

    @Test
    @DisplayName("POST /votes: CONFIRM vote → 201 with vote response")
    void castVote_confirm_returns201() throws Exception {
        VoteDTO.Response response = VoteDTO.Response.builder()
                .voteId(UUID.randomUUID())
                .reportId(REPORT_ID)
                .userId(VOTER_ID)
                .voteType(VoteType.CONFIRM)
                .createdAt(Instant.now())
                .build();

        when(jwtUtil.extractUserId(any())).thenReturn(VOTER_ID);
        when(verificationService.castVote(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteType").value("CONFIRM"))
                .andExpect(jsonPath("$.reportId").value(REPORT_ID.toString()));
    }

    @Test
    @DisplayName("POST /votes: DISPUTE vote → 201 with vote response")
    void castVote_dispute_returns201() throws Exception {
        VoteDTO.Response response = VoteDTO.Response.builder()
                .voteId(UUID.randomUUID())
                .reportId(REPORT_ID)
                .userId(VOTER_ID)
                .voteType(VoteType.DISPUTE)
                .createdAt(Instant.now())
                .build();

        when(jwtUtil.extractUserId(any())).thenReturn(VOTER_ID);
        when(verificationService.castVote(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"DISPUTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteType").value("DISPUTE"));
    }

    @Test
    @DisplayName("POST /votes: self-vote → 401 Unauthorized")
    void castVote_selfVote_returns401() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(VOTER_ID);
        when(verificationService.castVote(any(), any(), any()))
                .thenThrow(new UnauthorizedException("Cannot vote on own report"));

        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /votes: duplicate vote → 409 Conflict")
    void castVote_duplicateVote_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(VOTER_ID);
        when(verificationService.castVote(any(), any(), any()))
                .thenThrow(new IllegalStateException("User has already voted on this report"));

        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /votes: unknown report → 404 Not Found")
    void castVote_unknownReport_returns404() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(VOTER_ID);
        when(verificationService.castVote(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Report not found"));

        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /votes: missing voteType field → 400 Bad Request")
    void castVote_missingVoteType_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /votes: invalid voteType string value → 400 Bad Request")
    void castVote_invalidVoteTypeValue_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /votes: report in terminal state (EXPIRED) → 409 Conflict")
    void castVote_terminalStateReport_returns409() throws Exception {
        when(jwtUtil.extractUserId(any())).thenReturn(VOTER_ID);
        when(verificationService.castVote(any(), any(), any()))
                .thenThrow(new IllegalStateException(
                        "Cannot vote on report in terminal state: EXPIRED"));

        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /votes: no Authorization header → 400 Bad Request (missing required header)")
    void castVote_noAuthHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /votes: non-Bearer auth header → 401 Unauthorized")
    void castVote_nonBearerAuthHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/reports/{reportId}/votes", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isUnauthorized());
    }
}
