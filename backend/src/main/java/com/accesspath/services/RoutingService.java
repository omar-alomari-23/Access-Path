package com.accesspath.services;

import com.accesspath.dto.RouteDTO;
import com.accesspath.repositories.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final ReportRepository reportRepository;

    private static final double WARNING_RADIUS = 50.0;

    public RouteDTO.Response getDemoRoutes(RouteDTO.Request request) {
        List<RouteDTO.RouteOption> routes = new ArrayList<>();

        // Generate 3 demo routes with different characteristics
        routes.add(buildRoute(
            "A",
            "Accessible Route",
            request,
            true,
            List.of(
                "Head north on Main Street",
                "Turn left onto Accessible Path",
                "Continue straight for 200m",
                "Arrive at destination"
            )
        ));

        routes.add(buildRoute(
            "B",
            "Shorter Route",
            request,
            false,
            List.of(
                "Head east on High Street",
                "Turn right at the junction",
                "Continue for 150m",
                "Arrive at destination"
            )
        ));

        routes.add(buildRoute(
            "C",
            "Scenic Route",
            request,
            true,
            List.of(
                "Head south through the park",
                "Follow the accessible path",
                "Turn left at the fountain",
                "Arrive at destination"
            )
        ));

        // Rank by suitability score
        routes = rankBySuitability(routes);

        log.info("Generated {} demo routes", routes.size());
        return RouteDTO.Response.builder()
                .routes(routes)
                .build();
    }

    public List<RouteDTO.RouteOption> rankBySuitability(
            List<RouteDTO.RouteOption> routes) {
        return routes.stream()
                .sorted((a, b) -> Double.compare(
                    b.getSuitabilityScore(),
                    a.getSuitabilityScore()
                ))
                .toList();
    }

    private RouteDTO.RouteOption buildRoute(
            String routeId,
            String name,
            RouteDTO.Request request,
            boolean accessible,
            List<String> steps) {

        // Build waypoints between origin and destination
        List<RouteDTO.WayPoint> waypoints = List.of(
            RouteDTO.WayPoint.builder()
                .latitude(request.getOriginLat())
                .longitude(request.getOriginLng())
                .build(),
            RouteDTO.WayPoint.builder()
                .latitude((request.getOriginLat()
                    + request.getDestinationLat()) / 2)
                .longitude((request.getOriginLng()
                    + request.getDestinationLng()) / 2)
                .build(),
            RouteDTO.WayPoint.builder()
                .latitude(request.getDestinationLat())
                .longitude(request.getDestinationLng())
                .build()
        );

        // Get warnings from nearby verified reports
        List<RouteDTO.Warning> warnings = getWarningsForRoute(
            request.getOriginLat(),
            request.getOriginLng()
        );

        // Calculate suitability score
        double suitability = calculateSuitability(
            accessible, warnings, request
        );

        // More warnings = lower confidence, clamped between 0.1 and 0.8
        float confidence = Math.max(0.1f, 0.8f - (warnings.size() * 0.1f));

        return RouteDTO.RouteOption.builder()
                .routeId(routeId)
                .name(name)
                .waypoints(waypoints)
                .steps(steps)
                .suitabilityScore(suitability)
                .warnings(warnings)
                .confidenceScore(confidence)
                .estimatedMinutes(accessible ? 12 : 8)
                .build();
    }

    private List<RouteDTO.Warning> getWarningsForRoute(
            double lat, double lng) {

        return reportRepository
            .findVerifiedNearby(lat, lng, WARNING_RADIUS)
            .stream()
            .map(report -> RouteDTO.Warning.builder()
                .category(report.getCategory())
                .severity(report.getSeverity())
                .latitude(report.getLocationPoint() != null
                    ? report.getLocationPoint().getY() : lat)
                .longitude(report.getLocationPoint() != null
                    ? report.getLocationPoint().getX() : lng)
                .description(report.getDescription())
                .build()
            )
            .toList();
    }

    private double calculateSuitability(
            boolean accessible,
            List<RouteDTO.Warning> warnings,
            RouteDTO.Request request) {

        double score = accessible ? 0.8 : 0.5;

        // Reduce score per warning
        score -= warnings.size() * 0.1;

        // Boost if user preferences match route
        if (Boolean.TRUE.equals(request.getAvoidStairs()) && accessible) {
            score += 0.2;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }
}