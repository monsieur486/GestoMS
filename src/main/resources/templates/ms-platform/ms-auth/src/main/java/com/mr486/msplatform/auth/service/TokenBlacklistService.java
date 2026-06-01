package com.mr486.msplatform.auth.service;
import com.mr486.msplatform.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;

/**
 * Service de gestion de la liste noire des tokens et du stockage des refresh tokens
 * opaques dans Redis. Assure la révocation des access tokens (via JTI) et la rotation
 * atomique des refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    /**
     * Ajoute le JTI d'un access token à la liste noire Redis avec une expiration.
     *
     * @param jti        l'identifiant unique du token JWT à blacklister
     * @param ttlSeconds la durée de vie restante du token en secondes
     */
    public void blacklist(String jti, long ttlSeconds) {
        redis.opsForValue().set(RedisKeys.authBlacklist(jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Stocke le refresh token Keycloak sous clé opaque dans Redis avec une expiration.
     *
     * @param opaqueToken    la clé opaque générée côté ms-auth
     * @param kcRefreshToken le refresh token Keycloak à conserver
     * @param ttlSeconds     la durée de vie du refresh token en secondes
     */
    public void storeRefreshToken(String opaqueToken, String kcRefreshToken, long ttlSeconds) {
        redis.opsForValue().set(
                RedisKeys.authRefresh(opaqueToken), kcRefreshToken, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Récupère le refresh token Keycloak associé à un token opaque sans le supprimer.
     *
     * @param opaqueToken la clé opaque dont on veut le refresh token
     * @return un {@link Optional} contenant le refresh token, ou vide si absent/expiré
     */
    public Optional<String> getRefreshToken(String opaqueToken) {
        return Optional.ofNullable(redis.opsForValue().get(RedisKeys.authRefresh(opaqueToken)));
    }

    /**
     * Récupère et supprime atomiquement le refresh token Keycloak associé à un token
     * opaque (opération GETDEL garantissant qu'un seul usage est possible).
     *
     * @param opaqueToken la clé opaque à consommer
     * @return un {@link Optional} contenant le refresh token, ou vide si absent/expiré
     */
    public Optional<String> getAndDeleteRefreshToken(String opaqueToken) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(RedisKeys.authRefresh(opaqueToken)));
    }

    /**
     * Supprime le refresh token opaque de Redis (utilisé lors du logout).
     *
     * @param opaqueToken la clé opaque à supprimer
     */
    public void deleteRefreshToken(String opaqueToken) {
        redis.delete(RedisKeys.authRefresh(opaqueToken));
    }
}
