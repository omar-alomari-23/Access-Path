package com.accesspath.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

import com.accesspath.validation.ValidCoordinates;

public class RouteDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotNull(message = "Origin latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double originLat;

        @NotNull(message = "Origin longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double originLng;

        @NotNull(message = "Destination latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double destinationLat;

        @NotNull(message = "Destination longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double destinationLng;

        // Route preferences from F-1
        private Boolean avoidStairs;
        private Boolean avoidSteepInclines;
        private Boolean avoidUnsafeAreas;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RouteOption {
        private String routeId;
        private String name;
        private List<WayPoint> waypoints;
        private List<String> steps;
        private double suitabilityScore;
        private List<Warning> warnings;
        private Float confidenceScore;
        private int estimatedMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WayPoint {
        private Double latitude;
        private Double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Warning {
        private String category;
        private String severity;
        private Double latitude;
        private Double longitude;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private List<RouteOption> routes;
    }
}