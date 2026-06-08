package com.accesspath.dto;

import com.accesspath.models.Vote.VoteType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class VoteDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotNull(message = "Vote type is required")
        private VoteType voteType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID voteId;
        private UUID reportId;
        private UUID userId;
        private VoteType voteType;
        private Instant createdAt;
    }
}