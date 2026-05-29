---
name: ms-auth-design
description: "Non-obvious design rationale behind the ms-auth service (Keycloak wrapper) and the gateway TokenBlacklistFilter — the WHY behind choices that aren't visible in the code"
metadata: 
  node_type: memory
  type: project
  originSessionId: 6d7e15f7-0812-4957-97ca-e94b5a021f4a
---

The ms-auth service in the GestoMS template ZIP wraps Keycloak's password grant. The implementation choices below are deliberate and non-obvious from reading the code alone — preserve their intent if extending this feature.

**Why opaque refresh tokens (UUID) instead of forwarding Keycloak refresh tokens to clients.**
Clients never see the real Keycloak refresh token. On `/auth/login`, ms-auth stores the KC refresh token in Redis under `auth:refresh:{uuid}` and returns the UUID to the client. This lets the platform retain control: deleting the Redis entry forces logout even if the client still holds the UUID, and the KC refresh token (long-lived secret) never appears in client logs or storage. The spec is at `docs/superpowers/specs/2026-05-29-keycloak-refresh-logout-redis-design.md`.

**Why `getAndDelete` (Redis GETDEL) in refresh rotation, not separate get+delete.**
Two concurrent `/auth/refresh` calls with the same opaque UUID would both succeed with non-atomic read+delete, leaving one orphaned new UUID in Redis. This matters because the realm sets `refreshTokenMaxReuse=0` (each Keycloak refresh token can be used exactly once). The atomic GETDEL closes the TOCTOU race so only one of the concurrent calls reads the value.

**Why the gateway parses `jti` with manual Base64 + string scan instead of using a JWT library.**
The gateway never verifies signatures — that's done downstream by resource servers via JWKS. Adding a JWT library (~MB of deps) just to extract one claim for the blacklist check is overkill. The `TokenBlacklistFilter` only needs to check `auth:blacklist:{jti}` in Redis; if the JTI is wrong or absent, the resource server's signature check catches it anyway.

**Why the gateway hardcodes `"auth:blacklist:"` instead of importing `RedisKeys` from common-lib.**
Deliberate. The gateway is reactive (WebFlux) and intentionally has a minimal dependency surface — adding common-lib pulls in JPA/Lombok transitive deps that don't belong on the gateway. The constant is duplicated (gateway hardcoded vs ms-auth using `RedisKeys.authBlacklist()`); if the prefix changes, both must update. Worth a comment but not abstraction.

**Why ms-auth depends on Keycloak feature flag in the generator.**
`FeatureFilterProcessor` excludes `ms-auth/` when `keycloak=false`. ms-auth wraps Keycloak's password grant — without Keycloak there is nothing to wrap. Both directories (`keycloak/` and `ms-auth/`) are filtered by the same condition.

**Redis key conventions (defined in [[redis-key-conventions]] if extended):**
- `auth:blacklist:{jti}` → value `"1"`, TTL = JWT remaining lifetime
- `auth:refresh:{uuid}` → value = KC refresh token (plain string), TTL = KC refresh_expires_in

**Realm JSON token lifespans (in `keycloak/import/ms-realm-realm.json`):**
- `accessTokenLifespan: 300` (5 min — fast revocation window)
- `ssoSessionMaxLifespan: 1800` (30 min — refresh token lifetime)
- `refreshTokenMaxReuse: 0` (force rotation, no reuse)
