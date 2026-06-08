package com.accesspath.controllers;

import com.accesspath.dto.RouteDTO;
import com.accesspath.services.RoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Slf4j
public class RouteController {

    private final RoutingService routingService;

    @PostMapping
    public ResponseEntity<RouteDTO.Response> getRoutes(
            @Valid @RequestBody RouteDTO.Request request) {
        log.info("Route request from ({},{}) to ({},{})",
            request.getOriginLat(), request.getOriginLng(),
            request.getDestinationLat(), request.getDestinationLng()
        );
        RouteDTO.Response response = routingService.getDemoRoutes(request);
        return ResponseEntity.ok(response);
    }
}
