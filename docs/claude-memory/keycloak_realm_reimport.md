---
name: keycloak-realm-reimport
description: "Generated platform's Keycloak imports the realm only into an EMPTY db — a surviving keycloak_db_data volume means regenerated realm changes (new per-service users/roles) are silently NOT applied"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3dcf1186-bdaa-431b-81d7-378cfbb226f1
---

The generated platform runs Keycloak with `start-dev --import-realm`, importing `keycloak/import/ms-realm-realm.json` mounted at `/opt/keycloak/data/import`. Keycloak imports a realm **only if it does not already exist in its database**. The realm is persisted in `keycloak-db` (a postgres container) backed by the named volume `keycloak_db_data`.

Consequence: if you regenerate the platform with different `resources[]` (which changes the realm — new `USER_<SERVICE>` roles and `test-<service>` users, see [[cross-cutting-config-pattern]]) but reuse a **surviving `keycloak_db_data` volume**, Keycloak does NOT re-import, so the new users/roles are absent.

**Signature symptom (looks like a generator bug but isn't):** `test-admin` and `test-batch` log in fine — they existed in the previous realm too — but the new per-service users (`test-order-service`, …) can't get a token / `./test-all.sh` fails at the first per-service `check_token` ("Unable to get ORDER_SERVICE token"). Verify the generated realm JSON first; if `test-<service>` is present and well-formed (same shape as `test-admin`), the generator is fine and the cause is the stale volume.

**Fix (runtime/ops, not code):** `./clean-docker.sh` (does `docker compose down -v` + volume prune) then bring the stack back up, forcing a fresh realm import. Same hazard family as [[keycloak-hostname-iss-match]] — Keycloak state that looks generation-driven but is actually pinned by a persistent volume.
