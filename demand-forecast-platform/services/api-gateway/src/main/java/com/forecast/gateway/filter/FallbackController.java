package com.forecast.gateway.filter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Handles circuit breaker fallback responses.
 * When a downstream service's circuit is OPEN, gateway routes here instead.
 * Returns a clean 503 with enough info for the client to retry.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/service-unavailable")
    public ResponseEntity<Map<String, Object>> serviceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "message", "The service is temporarily unavailable. Please try again later.",
                "timestamp", Instant.now().toString()
        ));
    }
}
