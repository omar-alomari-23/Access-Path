package com.accesspath.controllers;

import com.accesspath.dto.VoteDTO;
import com.accesspath.exceptions.UnauthorizedException;
import com.accesspath.security.JwtUtil;
import com.accesspath.services.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class VoteController {

    private final VerificationService verificationService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{reportId}/votes")
    public ResponseEntity<VoteDTO.Response> castVote(
            @PathVariable UUID reportId,
            @Valid @RequestBody VoteDTO.CreateRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        VoteDTO.Response response = verificationService.castVote(
            reportId, userId, request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return jwtUtil.extractUserId(authHeader.substring(7));
    }
}
