package com.forecast.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    /**
     * Kimlik doğrulanmış istekler için rate limiting anahtarı.
     *
     * AuthenticationFilter JWT'yi doğrulayıp X-User-Id header'ını ekledikten sonra
     * bu resolver devreye girer. Her kullanıcı kendi limitine sahip olur.
     *
     * Örnek: user-A 100 istek/sn hakkı tüketse bile user-B etkilenmez.
     * IP bazlı rate limiting'de ise aynı ofis ağındaki 100 kullanıcı
     * toplu limiti paylaşır.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // AuthenticationFilter tarafından eklenen X-User-Id header'ını kullan
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

            if (userId != null && !userId.isBlank()) {
                // Kimlik doğrulanmış: kullanıcı bazlı limit
                return Mono.just("user:" + userId);
            }

            // Kimlik doğrulanmamış: IP bazlı limite geri dön
            // (login endpoint'i gibi public route'lar için)
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "anonymous";
            return Mono.just("ip:" + ip);
        };
    }
}
