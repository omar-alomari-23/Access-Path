package com.accesspath.dto;

import com.accesspath.models.Report.ReportStatus;
import com.accesspath.validation.HasCoordinates;
import com.accesspath.validation.ValidCoordinates;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

public class ReportDTO {

    @ValidCoordinates
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest implements HasCoordinates {
        @NotBlank(message = "Description is required")
        private String description;

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
        private Double longitude;

        // Optional — can be overridden by ClassificationService
        private String category;
        private String severity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DraftResponse {
        // Returned after ClassificationService suggests category/severity
        private String suggestedCategory;
        private String suggestedSeverity;
        private boolean confidenceFlag;
        private Double latitude;
        private Double longitude;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID reportId;
        private UUID reporterUserId;
        private String category;
        private String severity;
        private String description;
        private Double latitude;
        private Double longitude;
        private ReportStatus status;
        private Float confidenceScore;
        private Instant createdAt;
        private Instant expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NearbyResponse {
        private UUID reportId;
        private String category;
        private String severity;
        private ReportStatus status;
        private Double latitude;
        private Double longitude;
        private Float confidenceScore;
        private Instant createdAt;
    }
}