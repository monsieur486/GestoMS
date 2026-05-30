package com.mr486.msplatform.auth.service;
import com.mr486.msplatform.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    public void blacklist(String jti, long ttlSeconds) {
        redis.opsForValue().set(RedisKeys.authBlacklist(jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    public void storeRefreshToken(String opaqueToken, String kcRefreshToken, long ttlSeconds) {
        redis.opsForValue().set(RedisKeys.authRefresh(opaqueToken), kcRefreshToken, Duration.ofSeconds(ttlSeconds));
    }

    public Optional<String> getRefreshToken(String opaqueToken) {
        return Optional.ofNullable(redis.opsForValue().get(RedisKeys.authRefresh(opaqueToken)));
    }

    public Optional<String> getAndDeleteRefreshToken(String opaqueToken) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(RedisKeys.authRefresh(opaqueToken)));
    }

    public void deleteRefreshToken(String opaqueToken) {
        redis.delete(RedisKeys.authRefresh(opaqueToken));
    }
}
