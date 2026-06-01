---
name: ms-auth-password-change
description: "Lot 2 password management — why ms-auth gained master admin-cli access for self password-change, the 422-not-401 contract, and the duplicated KeycloakAdminClient; admin force-reset already lived in admin-application"
metadata:
  node_type: memory
  type: project
---

Lot 2 (commits `684716e` → `afef53a`) added password management. Two non-obvious facts the code alone won't explain — preserve their intent if extending.

**Admin user management already exists in `admin-application`, NOT `ms-webui`.**
`admin-application` (always installed) has the full user admin: `UsersController` + `users.html` + `edit.html` (list/search/create/delete/edit) and password **reset** via its `KeycloakAdminClient.resetPassword` (`temporary:false`). The Lot 2 "admin forces a new password" requirement was therefore ~95% already there — Lot 2 only added a second "retype" confirmation field + a server-side match guard (`redirect …/edit?mismatch`) to the existing reset card. Do NOT build a user-admin UI in `ms-webui`. The original spec wrongly assumed it didn't exist (exploration missed `admin-application`); spec was corrected in `f8b565e`.

**`ms-auth` self password-change reuses MASTER admin creds, not a realm-management service account.**
`POST /auth/account/password` (self-service, called by `ms-webui`'s "Mon compte" form) verifies the old password via a password grant on `preferred_username`, then sets the new one via the Keycloak Admin API. It does this with the **master `admin-cli` password grant** (`KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`), exactly like `admin-application`. This was a deliberate choice over the originally-specced `realm-management` service-account roles on `ms-gateway`: reusing master creds means **zero realm-JSON changes** (and avoids the stale-`keycloak_db_data` re-import gotcha, see [[keycloak-realm-reimport]]). `ms-auth`'s `KeycloakAdminClient` is a **reduced copy** of `admin-application`'s (just `adminToken()` + `resetPassword()`); the two are independent generated modules with no shared lib for this, so the duplication is intentional — but **keep `adminToken()` in sync** between them.

**Wrong old password returns 422, never 401.**
If `/auth/account/password` returned 401 for a bad old password, `ms-webui`'s `GatewayClient` would read it as "access token expired", trigger a refresh + replay, and on a second 401 log the user out. So ms-auth maps the failed verify grant to **422 UNPROCESSABLE_ENTITY**, and `ms-webui` calls the endpoint through a dedicated `MsAuthClient.changePassword` (mapping 422→wrong-old, 401→token-expired) **instead of** `GatewayClient`.

**`ms-auth` had no tests before Lot 2 → its pom lacked `spring-boot-starter-test`.**
Adding the first ms-auth test (`AuthServiceChangePasswordTest`) required adding `spring-boot-starter-test` (test scope) to `ms-auth/pom.xml` (`afef53a`). The generator's layout guards ([[generated-files-layout-guard]]) can NOT catch a missing test dependency because template Java is never compiled by `mvn test` — only an **e2e generate + `mvn -pl … -am test-compile`** of the generated platform surfaces it. Always test-compile the generated modules when adding the first test to a module.

**The ms-gateway access tokens carry NO `sub` claim — never use `getSubject()` to identify the user.**
Found via the `test-all.sh` admin2 smoke (it returned 500, not 204). The `ms-gateway` client's tokens contain `preferred_username`, `realm_access.roles`, `azp`, etc. but **no `sub`** (decode any token to confirm). So `changeOwnPassword` originally did `userId = jwt.getSubject()` → null → `PUT /admin/realms/ms-realm/users/null/reset-password` → 404 → swallowed by `KeycloakAdminClient`'s `catch (Exception)` → generic **500**. Fix (`fd0cc5b`): resolve the id via `KeycloakAdminClient.findUserId(preferred_username)` (Admin API exact search), never trust `sub`. Two lessons: (1) this is exactly the class of bug the layout guards can't see and only the generated-platform e2e/Docker run catches — the smoke test earned its keep; (2) the `catch (Exception e) { throw new KeycloakUnavailableException(); }` pattern (copied from admin-application) **discards the cause**, which cost several debugging round-trips — chaining the cause (`new KeycloakUnavailableException(e)`) + logging the HTTP status is a worthwhile follow-up in both modules. Why `sub` is absent (ms-gateway client mapper / lightweight token config in the realm JSON) is a separate, still-open question — the fix deliberately doesn't depend on it.

Related: [[ms-auth-design]], [[admin-realm-roles]], [[generated-files-layout-guard]], [[keycloak-realm-reimport]].
