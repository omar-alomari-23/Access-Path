package com.accesspath.dto;

import com.accesspath.models.ModerationAction.ActionType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class ModerationActionDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionRequest {
        // Optional note for moderation decision
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID actionId;
        private UUID reportId;
        private UUID moderatorUserId;
        private ActionType actionType;
        private String note;
        private Instant createdAt;
    }
}