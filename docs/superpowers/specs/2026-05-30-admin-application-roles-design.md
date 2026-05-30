# Phase 3c — `admin-application` : gestion des rôles

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** assigner / retirer des rôles realm à un utilisateur via la Keycloak Admin API, depuis une
page `/users/{id}/roles`. Dernier sous-bloc de la Phase 3.

## Contexte

`admin-application` (3a + 3b) est un module toujours installé, BFF réservé `ROLE_ADMIN`, qui liste, crée
et supprime des utilisateurs Keycloak via `KeycloakAdminClient` (token admin master `admin-cli`,
RestTemplate brut). Rôles realm applicatifs de `ms-realm` : `ADMIN`, `USER_SERVICE_A/B/C`, `USER_BATCH`,
`SERVICE` (+ tout `USER_<resource>` généré). Keycloak ajoute des rôles built-in à l'exécution
(`default-roles-ms-realm`, `offline_access`, `uma_authorization`).

API role-mapping Keycloak : `GET /roles`, `GET /roles/{name}`, `GET/POST/DELETE
/users/{id}/role-mappings/realm` (corps = représentations `[{id,name}]`), `GET /users/{id}`.

## Décisions de design (validées)

### A. Page dédiée par utilisateur

`GET /users/{id}/roles` : rôles actuels (avec retrait) + rôles assignables (avec ajout). Lien « rôles »
depuis chaque ligne de `/users`.

### B. Rôles realm uniquement

Pas de rôles client, composites ni groupes.

### C. Filtrer les built-ins Keycloak

La liste assignable exclut `offline_access`, `uma_authorization` et le préfixe `default-roles-` ; ne
montre que les rôles applicatifs.

### D. Garde anti-verrouillage

Sur la page de l'utilisateur connecté, le bouton « Retirer » est masqué pour le rôle `ADMIN`
(`role.name == 'ADMIN' && user.username == currentUsername`) — évite de se verrouiller hors
d'admin-application (qui est `hasRole ADMIN`). Garde UI, cohérente avec l'anti-auto-suppression de 3b.

### E. POST-redirect-GET, 3 nouveaux fichiers, aucun changement générateur

Toutes les actions redirigent vers `/users/{id}/roles`. 3 fichiers ajoutés → parité `TemplateLoaderTest`
**169 → 172**. admin-application déjà installé → **aucune modification du générateur**.

## Architecture & flux

API Keycloak (RestTemplate, token admin master) :
- `GET /admin/realms/{realm}/roles` → tous les rôles realm ; **filtre** `offline_access`/
  `uma_authorization`/préfixe `default-roles-`.
- `GET /admin/realms/{realm}/users/{id}/role-mappings/realm` → rôles actuels.
- `GET /admin/realms/{realm}/roles/{name}` → résout la représentation `{id,name}`.
- `POST` / `DELETE /admin/realms/{realm}/users/{id}/role-mappings/realm` corps `[{id,name}]` → assigner /
  retirer.
- `GET /admin/realms/{realm}/users/{id}` → l'utilisateur cible (username).

Flux :
1. `GET /users/{id}/roles` → en-tête (username), rôles actuels (bouton « Retirer », masqué si
   `role.name=='ADMIN' && user.username==currentUsername`), rôles assignables = (realm filtrés) −
   (actuels), avec « Assigner ».
2. `POST /users/{id}/roles/add` (`roleName`, CSRF) → résout `{id,name}` → POST mapping →
   `redirect:/users/{id}/roles`.
3. `POST /users/{id}/roles/remove` (`roleName`, CSRF) → résout → DELETE mapping →
   `redirect:/users/{id}/roles`.
4. Erreur Keycloak → `redirect:/users/{id}/roles?error` (page : « Keycloak indisponible. »).

Sécurité : admin only (3a) ; garde anti-verrouillage ; token admin server-side ; CSRF sur les
formulaires.

## Composants

**Nouveaux fichiers (3) :**
- `…/adminapp/dto/KeycloakRole.java` — `record KeycloakRole(String id, String name)`
  (`@JsonIgnoreProperties(ignoreUnknown=true)`).
- `…/adminapp/web/RolesController.java` — `GET /users/{id}/roles` (modèle `user`, `userRoles`,
  `assignableRoles`, `currentUsername`) + `POST /users/{id}/roles/add` + `POST /users/{id}/roles/remove` ;
  catch `KeycloakUnavailableException` → `redirect:/users/{id}/roles?error` (POST) / `${error}` (GET).
- `…/admin-application/src/main/resources/templates/roles.html` — en-tête username, liste des rôles
  actuels (bouton « Retirer » par rôle, masqué via la garde), liste des rôles assignables (bouton
  « Assigner »), zone d'erreur (`${error}` + `${param.error}`), lien retour `/users`. Tous les formulaires
  portent le jeton CSRF ; valeurs via `th:text` (XSS-safe).

**Fichiers modifiés (3) :**
- `…/adminapp/service/KeycloakAdminClient.java` :
  - `+ getUser(String id) → KeycloakUser` (`GET /users/{id}`).
  - `+ listRealmRoles() → List<KeycloakRole>` (`GET /roles`, filtre les built-ins).
  - `+ listUserRealmRoles(String id) → List<KeycloakRole>` (`GET /users/{id}/role-mappings/realm`).
  - `+ addRealmRole(String userId, String roleName)` : résout `GET /roles/{roleName}` → `POST
    /users/{id}/role-mappings/realm` corps `[{id,name}]`.
  - `+ removeRealmRole(String userId, String roleName)` : résout → `DELETE
    /users/{id}/role-mappings/realm` corps `[{id,name}]`.
  - Échecs → `KeycloakUnavailableException` (réutilisé).
- `…/admin-application/src/main/resources/templates/users.html` : colonne actions `+` lien
  `<a th:href="@{/users/{id}/roles(id=${u.id})}">rôles</a>`.
- `…/adminapp/service/KeycloakAdminClientTest.java` : `+` tests (listRealmRoles filtre built-ins ;
  listUserRealmRoles ; addRealmRole POST `[{id,name}]` ; removeRealmRole DELETE).

## Intégration générateur

- **Aucune modif générateur** : admin-application déjà installé (3a).
- **`TemplateLoaderTest`** : parité **169 → 172** (3 nouveaux fichiers).
- Aucun test générateur nouveau.

## Gestion d'erreurs

- **Non-admin / non authentifié** : couvert par 3a (toute l'app ADMIN).
- **Keycloak indisponible / rôle introuvable / token refusé** → `KeycloakUnavailableException` →
  `redirect:/users/{id}/roles?error` (POST) ou `${error}` (GET) → « Keycloak indisponible. ».
- **Anti-verrouillage** : bouton « Retirer ADMIN » absent sur sa propre page (garde UI).
- **Rôle déjà assigné / déjà absent** : l'API Keycloak est idempotente ; la liste assignable exclut déjà
  les rôles actuels.
- **CSRF** activé sur les formulaires add/remove.

## Tests & vérification

- **Test embarqué `KeycloakAdminClientTest` étendu** (Mockito, hors-ligne) : `listRealmRoles` filtre
  `offline_access`/`uma_authorization`/`default-roles-*` ; `listUserRealmRoles` parse ; `addRealmRole`
  (résout par nom puis POST corps `[{id,name}]`, vérifié par ArgumentCaptor) ; `removeRealmRole` (DELETE).
  Les tests 3a/3b restent verts.
- **`TemplateLoaderTest`** parité **172**.
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=false` → `KeycloakRole`/`RolesController`/`roles.html` présents + lien « rôles » dans
    `users.html` ; `mvn -pl admin-application -am package` du projet généré **compile ET exécute
    `KeycloakAdminClientTest` vert** ; `docker compose config` valide.
  - Runtime (login admin → /users → rôles → assigner/retirer réel) : **manuel/optionnel** (stack +
    Keycloak) — noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Création / suppression de rôles realm (on assigne/retire des rôles existants).
- Rôles client, rôles composites, groupes.
- Garde serveur stricte anti-verrouillage (UI seulement).

## Fichiers touchés (Phase 3c)

**Template (nouveaux) :** `admin-application/src/main/java/.../dto/KeycloakRole.java`,
`.../web/RolesController.java`, `admin-application/src/main/resources/templates/roles.html`.

**Template (modifiés) :** `admin-application/src/main/java/.../service/KeycloakAdminClient.java`,
`admin-application/src/main/resources/templates/users.html`,
`admin-application/src/test/java/.../service/KeycloakAdminClientTest.java`.

**Tests générateur (modifiés) :** `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`
(parité 169 → 172).
