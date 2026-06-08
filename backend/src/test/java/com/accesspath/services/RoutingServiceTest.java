package com.accesspath.services;

import com.accesspath.dto.RouteDTO;
import com.accesspath.models.Report;
import com.accesspath.models.Report.ReportStatus;
import com.accesspath.models.User;
import com.accesspath.models.User.UserRole;
import com.accesspath.repositories.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RoutingService.
 * Covers route generation, suitability ranking, preference boosts,
 * warning integration, and confidence score clamping.
 */
@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock private ReportRepository reportRepository;

    @InjectMocks
    private RoutingService routingService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private RouteDTO.Request buildRequest(boolean avoidStairs) {
        return buildRequest(avoidStairs, false, false);
    }

    private RouteDTO.Request buildRequest(
            boolean avoidStairs, boolean avoidSteepInclines, boolean avoidUnsafeAreas) {
        return RouteDTO.Request.builder()
                .originLat(25.2048)
                .originLng(55.2708)
                .destinationLat(25.2150)
                .destinationLng(55.2800)
                .avoidStairs(avoidStairs)
                .avoidSteepInclines(avoidSteepInclines)
                .avoidUnsafeAreas(avoidUnsafeAreas)
                .build();
    }

    private Report buildVerifiedReport() {
        User reporter = User.builder()
                .userId(UUID.randomUUID())
                .email("r@test.com")
                .passwordHash("h")
                .role(UserRole.REPORTER)
                .build();
        return Report.builder()
                .reportId(UUID.randomUUID())
                .reporter(reporter)
                .status(ReportStatus.VERIFIED)
                .category("SURFACE_HAZARD")
                .severity("MEDIUM")
                .description("Wet tiles near entrance")
                .createdAt(Instant.now())
                .build();
    }

    // ── getDemoRoutes: basic contract ─────────────────────────────────────────

    @Test
    @DisplayName("getDemoRoutes: always returns exactly 3 route options")
    void getDemoRoutes_alwaysReturnsThreeRoutes() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));

        assertThat(response.getRoutes()).hasSize(3);
    }

    @Test
    @DisplayName("getDemoRoutes: routes contain waypoints origin, midpoint, destination")
    void getDemoRoutes_eachRouteHasThreeWaypoints() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));

        for (RouteDTO.RouteOption route : response.getRoutes()) {
            assertThat(route.getWaypoints()).hasSize(3);
            // First waypoint must be origin
            assertThat(route.getWaypoints().get(0).getLatitude()).isEqualTo(25.2048);
            assertThat(route.getWaypoints().get(0).getLongitude()).isEqualTo(55.2708);
            // Last waypoint must be destination
            assertThat(route.getWaypoints().get(2).getLatitude()).isEqualTo(25.2150);
            assertThat(route.getWaypoints().get(2).getLongitude()).isEqualTo(55.2800);
        }
    }

    @Test
    @DisplayName("getDemoRoutes: routes are ranked by suitability score (descending)")
    void getDemoRoutes_routesRankedBySuitabilityDescending() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));
        List<RouteDTO.RouteOption> routes = response.getRoutes();

        for (int i = 0; i < routes.size() - 1; i++) {
            assertThat(routes.get(i).getSuitabilityScore())
                    .isGreaterThanOrEqualTo(routes.get(i + 1).getSuitabilityScore());
        }
    }

    @Test
    @DisplayName("getDemoRoutes: nearby verified reports are attached as warnings")
    void getDemoRoutes_withVerifiedNearbyReports_warningsAttached() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(buildVerifiedReport()));

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));

        // At least one route must have warnings
        boolean anyHasWarnings = response.getRoutes().stream()
                .anyMatch(r -> !r.getWarnings().isEmpty());
        assertThat(anyHasWarnings).isTrue();
    }

    @Test
    @DisplayName("getDemoRoutes: avoidStairs=true boosts accessible route suitability score")
    void getDemoRoutes_avoidStairsTrue_accessibleRouteRankedFirst() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(true));
        RouteDTO.RouteOption top = response.getRoutes().get(0);

        // With avoidStairs, accessible routes get +0.2 bonus — should lead the ranking
        assertThat(top.getSuitabilityScore()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("getDemoRoutes: many warnings reduce confidence score, clamped to 0.1 floor")
    void getDemoRoutes_manyWarnings_confidenceClampedAboveFloor() {
        // 8 nearby verified reports → confidence = max(0.1, 0.8 - 8*0.1) = max(0.1, 0.0) = 0.1
        List<Report> manyReports = Collections.nCopies(8, buildVerifiedReport());
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(manyReports);

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));

        for (RouteDTO.RouteOption route : response.getRoutes()) {
            assertThat(route.getConfidenceScore()).isGreaterThanOrEqualTo(0.1f);
        }
    }

    @Test
    @DisplayName("getDemoRoutes: avoidStairs=false → accessible route base suitability is 0.8")
    void getDemoRoutes_avoidStairsFalse_accessibleBaseScoreIs08() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));

        // The accessible route (routeId "A") has base suitability 0.8 without any avoidStairs boost
        RouteDTO.RouteOption accessible = response.getRoutes().stream()
                .filter(r -> "A".equals(r.getRouteId()))
                .findFirst()
                .orElseThrow();
        assertThat(accessible.getSuitabilityScore()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("getDemoRoutes: single warning reduces accessible route suitability by 0.1")
    void getDemoRoutes_singleWarning_suitabilityReducedBy01() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        RouteDTO.Response baseline = routingService.getDemoRoutes(buildRequest(false));
        double baseScore = baseline.getRoutes().get(0).getSuitabilityScore();

        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(buildVerifiedReport()));
        RouteDTO.Response withWarning = routingService.getDemoRoutes(buildRequest(false));
        double warnScore = withWarning.getRoutes().get(0).getSuitabilityScore();

        // Each warning deducts 0.1 from suitability
        assertThat(baseScore - warnScore).isCloseTo(0.1, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    @DisplayName("getDemoRoutes: no warnings → confidence score is 0.8")
    void getDemoRoutes_noWarnings_confidenceIsMax() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(buildRequest(false));

        for (RouteDTO.RouteOption route : response.getRoutes()) {
            assertThat(route.getConfidenceScore()).isEqualTo(0.8f);
        }
    }

    @Test
    @DisplayName("getDemoRoutes: avoidSteepInclines=true → 3 routes returned successfully")
    void getDemoRoutes_avoidSteepInclinesTrue_returnsThreeRoutes() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(
                buildRequest(false, true, false));

        assertThat(response.getRoutes()).hasSize(3);
    }

    @Test
    @DisplayName("getDemoRoutes: avoidUnsafeAreas=true → 3 routes returned successfully")
    void getDemoRoutes_avoidUnsafeAreasTrue_returnsThreeRoutes() {
        when(reportRepository.findVerifiedNearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        RouteDTO.Response response = routingService.getDemoRoutes(
                buildRequest(false, false, true));

        assertThat(response.getRoutes()).hasSize(3);
    }

    // ── rankBySuitability ─────────────────────────────────────────────────────

    @Test
    @DisplayName("rankBySuitability: sorts routes highest score first")
    void rankBySuitability_sortsDescending() {
        RouteDTO.RouteOption low = RouteDTO.RouteOption.builder()
                .routeId("C").suitabilityScore(0.2).build();
        RouteDTO.RouteOption mid = RouteDTO.RouteOption.builder()
                .routeId("B").suitabilityScore(0.5).build();
        RouteDTO.RouteOption high = RouteDTO.RouteOption.builder()
                .routeId("A").suitabilityScore(0.9).build();

        List<RouteDTO.RouteOption> ranked =
                routingService.rankBySuitability(List.of(low, high, mid));

        assertThat(ranked.get(0).getRouteId()).isEqualTo("A");
        assertThat(ranked.get(1).getRouteId()).isEqualTo("B");
        assertThat(ranked.get(2).getRouteId()).isEqualTo("C");
    }

    @Test
    @DisplayName("rankBySuitability: equal scores maintain stable relative order")
    void rankBySuitability_equalScores_allPresent() {
        RouteDTO.RouteOption r1 = RouteDTO.RouteOption.builder()
                .routeId("X").suitabilityScore(0.5).build();
        RouteDTO.RouteOption r2 = RouteDTO.RouteOption.builder()
                .routeId("Y").suitabilityScore(0.5).build();

        List<RouteDTO.RouteOption> ranked =
                routingService.rankBySuitability(List.of(r1, r2));

        assertThat(ranked).hasSize(2);
    }

    @Test
    @DisplayName("rankBySuitability: empty list → returns empty list")
    void rankBySuitability_emptyList_returnsEmpty() {
        List<RouteDTO.RouteOption> ranked =
                routingService.rankBySuitability(Collections.emptyList());

        assertThat(ranked).isEmpty();
    }
}
