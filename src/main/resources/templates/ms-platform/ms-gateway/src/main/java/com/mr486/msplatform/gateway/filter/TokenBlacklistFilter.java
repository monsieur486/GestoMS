package com.mr486.msplatform.gateway.filter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TokenBlacklistFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redis;

    public TokenBlacklistFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String jti = extractJti(authHeader.substring(7));
        if (jti == null) {
            return chain.filter(exchange);
        }
        return redis.hasKey("auth:blacklist:" + jti)
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        DataBuffer buffer = exchange.getResponse().bufferFactory()
                                .wrap("{\"error\":\"token_revoked\"}".getBytes(StandardCharsets.UTF_8));
                        return exchange.getResponse().writeWith(Mono.just(buffer));
                    }
                    return chain.filter(exchange);
                });
    }

    private String extractJti(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int jtiIdx = payload.indexOf("\"jti\"");
            if (jtiIdx < 0) return null;
            int colonIdx = payload.indexOf(':', jtiIdx);
            int startQuote = payload.indexOf('"', colonIdx);
            int endQuote = payload.indexOf('"', startQuote + 1);
            if (startQuote < 0 || endQuote < 0) return null;
            return payload.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            return null;
        }
    }
}
