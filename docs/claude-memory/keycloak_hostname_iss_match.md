---
name: keycloak-hostname-iss-match
description: "Why KC_HOSTNAME in the template docker-compose must include the external port — Keycloak puts that URL in the JWT iss claim, and resource servers reject tokens with mismatched iss"
metadata: 
  node_type: memory
  type: project
  originSessionId: 6d7e15f7-0812-4957-97ca-e94b5a021f4a
---

The template `docker-compose.yml` sets `KC_HOSTNAME: http://localhost:8089` for the keycloak service. This is **load-bearing** — do not simplify it back to `localhost` (the previous value) without understanding the failure mode.

**What goes wrong if KC_HOSTNAME omits the port.**
Keycloak fills the JWT `iss` claim from `KC_HOSTNAME` + its listening port (8080 inside the container). With `KC_HOSTNAME: localhost`, tokens get `iss = http://localhost:8080/realms/ms-realm`. The resource servers (service-a/b/c, service-consumer, service-batch, ms-auth itself when validating logout JWTs) all set `spring.security.oauth2.resourceserver.jwt.issuer-uri = http://localhost:8089/realms/ms-realm` (the externally-mapped port). Spring Security rejects every token with 401 because the iss claim does not match the configured issuer URI.

**Why the original test-all.sh worked anyway, and why ours doesn't without the fix.**
The pre-ms-auth `test-all.sh` called Keycloak's token endpoint *directly from the host* on port 8089. Keycloak honored the request's Host header (no `KC_HOSTNAME` override at the time, or the port matched), so iss came out as `http://localhost:8089`, matching the resource servers. With ms-auth in the loop, ms-auth calls Keycloak from *inside* the docker network via `http://keycloak:8080`. Keycloak then uses the configured `KC_HOSTNAME` for the iss URL, producing `http://localhost:8080` (wrong port) and breaking validation.

**Why the fix is `KC_HOSTNAME: http://localhost:8089` specifically.**
Setting `KC_HOSTNAME` to the full URL form (with scheme and port) makes Keycloak emit that exact URL in iss regardless of how the request arrived (internal docker hostname vs external host port). The internal URL `http://keycloak:8080` is still used for *transport* (it's what ms-auth dials), but the token's iss is now constant. Resource servers validate against `http://localhost:8089` and accept. The alternative — changing every resource server's issuer-uri to `http://localhost:8080` — would break external token use cases and is wrong.

**How to verify if a token problem might be this.**
Decode the access token's payload (`echo $TOKEN | cut -d. -f2 | base64 -d | jq`) and compare its `iss` claim to the resource servers' `KEYCLOAK_ISSUER_URI` env. If they differ in scheme, host, or port, this is the bug. The symptom is a uniform 401 across every protected endpoint while ms-auth login/refresh themselves succeed.

**Verified end-to-end.**
A freshly generated default platform passes the full `test-all.sh` suite (role matrix, refresh, logout, blacklist, stale-refresh) only after this fix. Without it, the very first `assert_http 'ADMIN can access service-a' 200` fails with 401.

Related: [[ms-auth-design]] for the broader auth architecture.
