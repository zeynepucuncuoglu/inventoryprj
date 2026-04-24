package com.forecast.gateway.filter;

import com.forecast.gateway.security.JwtTokenValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Custom Gateway Filter — runs before the request is forwarded to any downstream service.
 *
 * What it does:
 * 1. Extracts the Bearer token from Authorization header
 * 2. Validates it (signature + expiry)
 * 3. If valid: adds X-User-Id and X-User-Role headers for downstream services
 * 4. If invalid: returns 401 immediately — downstream never sees the request
 *
 * Downstream services trust these headers because:
 * - They're on a private Docker network (not reachable from internet)
 * - Only the gateway can set X-Gateway-Source header
 * - In production: mTLS between gateway and services for stronger trust
 */
@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtTokenValidator jwtValidator;

    public AuthenticationFilter(JwtTokenValidator jwtValidator) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.debug("Missing or malformed Authorization header for path: {}",
                        request.getPath());
                return unauthorized(exchange, "Missing Authorization header");
            }

            String token = authHeader.substring(7);

            if (!jwtValidator.isValid(token)) {
                log.debug("Invalid JWT token for path: {}", request.getPath());
                return unauthorized(exchange, "Invalid or expired token");
            }

            String userId = jwtValidator.extractUserId(token);
            String role   = jwtValidator.extractRole(token);

            // Mutate the request: add user context headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role != null ? role : "USER")
                    // Remove the Authorization header if downstream doesn't need it
                    // (optional — keep it if downstream services also validate)
                    .build();

            log.debug("Authenticated request: userId={} role={} path={}",
                    userId, role, request.getPath());

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"status":401,"error":"Unauthorized","message":"%s"}
                """.formatted(message);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes))
        );
    }

    public static class Config {
        // No config properties needed for this filter
    }
}
