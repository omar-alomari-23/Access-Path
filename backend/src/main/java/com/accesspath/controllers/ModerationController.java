package com.accesspath.controllers;

import com.accesspath.dto.ModerationActionDTO;
import com.accesspath.dto.ReportDTO;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.security.JwtUtil;
import com.accesspath.services.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MODERATOR')")
public class ModerationController {

    private final ModerationService moderationService;
    private final JwtUtil jwtUtil;

    @GetMapping("/queue")
    public ResponseEntity<List<ReportDTO.Response>> getQueue() {
        List<ReportDTO.Response> response =
            moderationService.getPendingQueue();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reportId}/verify")
    public ResponseEntity<ModerationActionDTO.Response> verify(
            @PathVariable UUID reportId,
            @RequestBody(required = false)
                ModerationActionDTO.ActionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID moderatorId = extractUserId(authHeader);
        if (request == null) request = new ModerationActionDTO.ActionRequest();
        return ResponseEntity.ok(
            moderationService.verify(reportId, moderatorId, request)
        );
    }

    @PostMapping("/{reportId}/reject")
    public ResponseEntity<ModerationActionDTO.Response> reject(
            @PathVariable UUID reportId,
            @RequestBody(required = false)
                ModerationActionDTO.ActionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID moderatorId = extractUserId(authHeader);
        if (request == null) request = new ModerationActionDTO.ActionRequest();
        return ResponseEntity.ok(
            moderationService.reject(reportId, moderatorId, request)
        );
    }

    @PostMapping("/{reportId}/resolve")
    public ResponseEntity<ModerationActionDTO.Response> resolve(
            @PathVariable UUID reportId,
            @RequestBody(required = false)
                ModerationActionDTO.ActionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID moderatorId = extractUserId(authHeader);
        if (request == null) request = new ModerationActionDTO.ActionRequest();
        return ResponseEntity.ok(
            moderationService.resolve(reportId, moderatorId, request)
        );
    }

    @PostMapping("/{reportId}/expire")
    public ResponseEntity<ModerationActionDTO.Response> expire(
            @PathVariable UUID reportId,
            @RequestHeader("Authorization") String authHeader) {
        UUID moderatorId = extractUserId(authHeader);
        return ResponseEntity.ok(
            moderationService.expire(reportId, moderatorId)
        );
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return jwtUtil.extractUserId(authHeader.substring(7));
    }
}
