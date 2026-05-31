package com.forecast.gateway.filter;

import com.forecast.gateway.security.RefreshTokenService;
import com.forecast.gateway.security.RefreshTokenService.InvalidRefreshTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Token yönetimi endpoint'leri.
 * Bu controller gateway'in kendisinde çalışır — downstream servislere gitmiyor.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RefreshTokenService refreshTokenService;

    public AuthController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * POST /api/v1/auth/refresh
     * Body: {"refreshToken": "uuid-token"}
     * Response: {"accessToken": "...", "refreshToken": "..."}
     *
     * Access token süresi dolunca çağrılır.
     * Her çağrıda token rotate edilir — eski refresh token geçersiz olur.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, String>>> refresh(
            @RequestBody Map<String, String> body) {

        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "refreshToken is required")));
        }

        return refreshTokenService.refresh(refreshToken)
                .map(pair -> ResponseEntity.ok(Map.of(
                        "accessToken",  pair.accessToken(),
                        "refreshToken", pair.refreshToken(),
                        "tokenType",    "Bearer"
                )))
                .onErrorResume(InvalidRefreshTokenException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("error", e.getMessage())))
                )
                .onErrorResume(Exception.class, e -> {
                    log.error("Refresh token error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Token refresh failed")));
                });
    }

    /**
     * POST /api/v1/auth/logout
     * Body: {"refreshToken": "uuid-token"}
     *
     * Refresh token Redis'ten silinir — kullanıcı oturumu tamamen kapanır.
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(
            @RequestBody Map<String, String> body) {

        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "refreshToken is required")));
        }

        return refreshTokenService.revoke(refreshToken)
                .map(revoked -> ResponseEntity.ok(
                        Map.of("message", revoked ? "Logged out" : "Token not found")
                ));
    }
}
