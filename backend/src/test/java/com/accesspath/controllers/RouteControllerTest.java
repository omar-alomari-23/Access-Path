package com.accesspath.controllers;

import com.accesspath.dto.RouteDTO;
import com.accesspath.security.JwtUtil;
import com.accesspath.security.UserDetailsServiceImpl;
import com.accesspath.services.RoutingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer unit tests for RouteController.
 * Covers accessible route request handling (US-9 / route planning feature).
 * Security filters are disabled — service logic is covered by RoutingServiceTest.
 */
@WebMvcTest(RouteController.class)
@AutoConfigureMockMvc(addFilters = false)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoutingService routingService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private static final String VALID_BODY =
            "{\"originLat\":25.2048,\"originLng\":55.2708," +
            "\"destinationLat\":25.2150,\"destinationLng\":55.2800," +
            "\"avoidStairs\":true,\"avoidSteepInclines\":false,\"avoidUnsafeAreas\":false}";

    // ── POST /api/routes ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/routes: valid request → 200 with route options")
    void getRoutes_validRequest_returns200WithRoutes() throws Exception {
        RouteDTO.RouteOption accessible = RouteDTO.RouteOption.builder()
                .routeId("A")
                .name("Accessible Route")
                .suitabilityScore(1.0)
                .confidenceScore(0.8f)
                .estimatedMinutes(12)
                .steps(List.of("Head north", "Arrive at destination"))
                .warnings(List.of())
                .build();

        RouteDTO.Response response = RouteDTO.Response.builder()
                .routes(List.of(accessible))
                .build();

        when(routingService.getDemoRoutes(any(RouteDTO.Request.class))).thenReturn(response);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes").isArray())
                .andExpect(jsonPath("$.routes[0].routeId").value("A"))
                .andExpect(jsonPath("$.routes[0].name").value("Accessible Route"))
                .andExpect(jsonPath("$.routes[0].suitabilityScore").value(1.0));
    }

    @Test
    @DisplayName("POST /api/routes: missing originLng → 400 Bad Request")
    void getRoutes_missingOriginLng_returns400() throws Exception {
        String body = "{\"originLat\":25.2048,\"destinationLat\":25.2150,\"destinationLng\":55.2800}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/routes: missing destinationLat → 400 Bad Request")
    void getRoutes_missingDestinationLat_returns400() throws Exception {
        String body = "{\"originLat\":25.2048,\"originLng\":55.2708,\"destinationLng\":55.2800}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/routes: missing originLat → 400 Bad Request")
    void getRoutes_missingOriginLat_returns400() throws Exception {
        String body = "{\"originLng\":55.2708,\"destinationLat\":25.2150,\"destinationLng\":55.2800}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/routes: missing destinationLng → 400 Bad Request")
    void getRoutes_missingDestinationLng_returns400() throws Exception {
        String body = "{\"originLat\":25.2048,\"originLng\":55.2708,\"destinationLat\":25.2150}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/routes: latitude out of range (>90) → 400 Bad Request")
    void getRoutes_latitudeOutOfRange_returns400() throws Exception {
        String body = "{\"originLat\":91.0,\"originLng\":55.2708," +
                      "\"destinationLat\":25.2150,\"destinationLng\":55.2800}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/routes: destination latitude out of range (>90) → 400 Bad Request")
    void getRoutes_destinationLatOutOfRange_returns400() throws Exception {
        String body = "{\"originLat\":25.2048,\"originLng\":55.2708," +
                      "\"destinationLat\":91.0,\"destinationLng\":55.2800}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/routes: destination longitude out of range (>180) → 400 Bad Request")
    void getRoutes_destinationLngOutOfRange_returns400() throws Exception {
        String body = "{\"originLat\":25.2048,\"originLng\":55.2708," +
                      "\"destinationLat\":25.2150,\"destinationLng\":181.0}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/routes: longitude out of range (<-180) → 400 Bad Request")
    void getRoutes_longitudeOutOfRange_returns400() throws Exception {
        String body = "{\"originLat\":25.2048,\"originLng\":-181.0," +
                      "\"destinationLat\":25.2150,\"destinationLng\":55.2800}";

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/routes: all coordinates at valid boundaries (±90/±180) → 200 OK")
    void getRoutes_boundaryCoordinates_returns200() throws Exception {
        String body = "{\"originLat\":90.0,\"originLng\":180.0," +
                      "\"destinationLat\":-90.0,\"destinationLng\":-180.0}";

        RouteDTO.Response response = RouteDTO.Response.builder()
                .routes(List.of())
                .build();

        when(routingService.getDemoRoutes(any())).thenReturn(response);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/routes: preferences omitted (all optional) → 200 OK")
    void getRoutes_preferencesOmitted_returns200() throws Exception {
        String minimalBody = "{\"originLat\":25.2048,\"originLng\":55.2708," +
                             "\"destinationLat\":25.2150,\"destinationLng\":55.2800}";

        RouteDTO.Response response = RouteDTO.Response.builder()
                .routes(List.of())
                .build();

        when(routingService.getDemoRoutes(any())).thenReturn(response);

        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes").isArray());
    }
}
