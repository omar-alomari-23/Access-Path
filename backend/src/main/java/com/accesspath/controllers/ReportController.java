package com.accesspath.controllers;

import com.accesspath.dto.ReportDTO;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.security.JwtUtil;
import com.accesspath.services.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final JwtUtil jwtUtil;

    // Step 1 — Get AI suggestion before submitting
    @PostMapping("/draft")
    public ResponseEntity<ReportDTO.DraftResponse> draftReport(
            @Valid @RequestBody ReportDTO.CreateRequest request) {
        ReportDTO.DraftResponse response = reportService.draftReport(request);
        return ResponseEntity.ok(response);
    }

    // Step 2 — Submit final report
    @PostMapping
    public ResponseEntity<ReportDTO.Response> createReport(
            @Valid @RequestBody ReportDTO.CreateRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        ReportDTO.Response response = reportService.createReport(
            request, userId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get report by ID
    @GetMapping("/{id}")
    public ResponseEntity<ReportDTO.Response> getReport(
            @PathVariable UUID id) {
        ReportDTO.Response response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    // Get nearby reports for duplicate awareness
    @GetMapping("/nearby")
    public ResponseEntity<List<ReportDTO.NearbyResponse>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "100.0") double radius) {
        List<ReportDTO.NearbyResponse> response =
            reportService.findNearby(lat, lng, radius);
        return ResponseEntity.ok(response);
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return jwtUtil.extractUserId(authHeader.substring(7));
    }
}
