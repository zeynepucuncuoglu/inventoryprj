package com.forecast.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * Refresh token yönetimi — Redis'te saklanır.
 *
 * Neden refresh token?
 * Access token kısa ömürlü (15 dakika) → güvenli, çalınsa bile kısa süre geçerli.
 * Kullanıcı her 15 dakikada login olmasın diye refresh token (7 gün) var.
 *
 * Akış:
 * 1. Login → access token (15dk) + refresh token (7gün) dön
 * 2. Access token süresi dolunca → /auth/refresh çağır
 * 3. Refresh token geçerliyse → yeni access token ver
 * 4. Logout → refresh token Redis'ten sil (invalidate)
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final String REDIS_PREFIX = "refresh_token:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public RefreshTokenService(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:900000}") long accessTokenExpiryMs) {
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    /**
     * Yeni refresh token oluştur ve Redis'e kaydet.
     * Key: refresh_token:{token} → Value: userId:role
     */
    public Mono<String> createRefreshToken(String userId, String role) {
        String token = UUID.randomUUID().toString();
        String value = userId + ":" + (role != null ? role : "USER");

        return redisTemplate.opsForValue()
                .set(REDIS_PREFIX + token, value, REFRESH_TOKEN_TTL)
                .map(saved -> token)
                .doOnSuccess(t -> log.debug("Refresh token created for userId={}", userId));
    }

    /**
     * Refresh token geçerliyse yeni access token döner.
     * Kullanılan refresh token silinir (rotation) — her token bir kez kullanılır.
     */
    public Mono<TokenPair> refresh(String refreshToken) {
        String key = REDIS_PREFIX + refreshToken;

        return redisTemplate.opsForValue()
                .getAndDelete(key)  // atomic get + delete (token rotation)
                .flatMap(value -> {
                    if (value == null) {
                        return Mono.error(new InvalidRefreshTokenException("Refresh token not found or expired"));
                    }

                    String[] parts = value.split(":", 2);
                    String userId = parts[0];
                    String role   = parts.length > 1 ? parts[1] : "USER";

                    // Yeni access token oluştur
                    String newAccessToken = issueAccessToken(userId, role);

                    // Yeni refresh token oluştur (rotation — eski artık geçersiz)
                    return createRefreshToken(userId, role)
                            .map(newRefreshToken -> new TokenPair(newAccessToken, newRefreshToken));
                })
                .doOnError(e -> log.warn("Refresh token invalid: {}", e.getMessage()));
    }

    /**
     * Logout: refresh token'ı Redis'ten sil.
     */
    public Mono<Boolean> revoke(String refreshToken) {
        return redisTemplate.delete(REDIS_PREFIX + refreshToken)
                .map(count -> count > 0)
                .doOnSuccess(deleted ->
                        log.debug("Refresh token revoked: deleted={}", deleted));
    }

    private String issueAccessToken(String userId, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public record TokenPair(String accessToken, String refreshToken) {}

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
