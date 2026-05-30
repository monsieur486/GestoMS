# Phase 4 — `admin-application` : édition d'utilisateur + reset password

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** étendre `admin-application` (3a–3c) pour **éditer** un utilisateur (email, prénom, nom,
actif) et **réinitialiser son mot de passe**, via la Keycloak Admin API, depuis une page
`/users/{id}/edit`. (Hors roadmap initiale — Phase « 4 » demandée par l'utilisateur.)

## Contexte

`admin-application` (3a–3c) est un module toujours installé, BFF réservé `ROLE_ADMIN`, qui liste / crée /
supprime des utilisateurs et gère leurs rôles via `KeycloakAdminClient` (token admin master `admin-cli`,
RestTemplate brut). `KeycloakUser{id,username,email,firstName,lastName,enabled}` et `getUser(id)`
existent. API Keycloak : update = `PUT /admin/realms/{realm}/users/{id}` (représentation) ; reset
password = `PUT /admin/realms/{realm}/users/{id}/reset-password` (`{type,value,temporary}`).

## Décisions de design (validées)

### A. Page dédiée `/users/{id}/edit`

Formulaire d'édition (email/prénom/nom/actif ; **username read-only**) + formulaire de reset password.
Endpoints ajoutés au `UsersController` existant (concern « user CRUD »). Lien « éditer » depuis chaque
ligne de `/users`.

### B. Update par GET-puis-PUT (préservation)

`updateUser` fait `GET /users/{id}` (représentation brute en `Map`), fusionne `email/firstName/lastName/
enabled`, puis `PUT /users/{id}` — préserve `username` et les autres attributs Keycloak.

### C. Reset password permanent

`PUT /users/{id}/reset-password` avec `{type:"password", value, temporary:false}` — utilisable
immédiatement (cohérent avec la création 3b).

### D. Garde anti-auto-désactivation

Sur la page de l'utilisateur connecté (`user.username == currentUsername`), la case « actif » est
**cochée + désactivée** et un `<input type="hidden" name="enabled" value="true">` force la valeur — évite
de se couper l'accès. Garde UI, cohérente avec les gardes anti-auto-suppression (3b) / anti-retrait-ADMIN
(3c).

### E. POST-redirect-GET, 1 nouveau fichier, aucun changement générateur

Toutes les actions redirigent vers `/users/{id}/edit` (`?updated` / `?pwd` / `?error`). 1 fichier ajouté
(`edit.html`) → parité `TemplateLoaderTest` **172 → 173**. admin-application déjà installé → **aucune
modification du générateur**.

## Architecture & flux

1. `GET /users/{id}/edit` → `getUser(id)` → modèle `user` (form pré-rempli, username read-only) +
   `currentUsername` ; catch `KeycloakUnavailableException` → `error`. Vue `edit`.
2. `POST /users/{id}/edit` (`email, firstName, lastName, enabled`, CSRF) → `updateUser(id, …)` →
   `redirect:/users/{id}/edit?updated` ; erreur → `redirect:/users/{id}/edit?error`.
3. `POST /users/{id}/password` (`password`, CSRF) → `resetPassword(id, password)` →
   `redirect:/users/{id}/edit?pwd` ; erreur → `redirect:/users/{id}/edit?error`.

Garde : sur sa propre page, `enabled` est verrouillé à `true` (case désactivée + champ caché). Sécurité :
admin only (3a) ; mot de passe server-side uniquement ; CSRF.

## Composants

**Nouveau fichier (1) :**
- `…/admin-application/src/main/resources/templates/edit.html` — form d'édition (username read-only ;
  email/prénom/nom ; case « actif » — pour soi : cochée + désactivée + `<input type="hidden"
  name="enabled" value="true">`) + form reset password (`password` required) ; bandeaux
  `${param.updated}` / `${param.pwd}` / `${param.error}` ; lien retour `/users`. CSRF sur les deux
  formulaires ; valeurs via `th:text`/`th:value` (XSS-safe).

**Fichiers modifiés (3) :**
- `…/adminapp/service/KeycloakAdminClient.java` :
  - `+ updateUser(String id, String email, String firstName, String lastName, boolean enabled)` :
    `GET /users/{id}` (`Map`) → `put` `email`/`firstName`/`lastName`/`enabled` → `PUT /users/{id}` (`Map`).
    Échec / body null → `KeycloakUnavailableException`.
  - `+ resetPassword(String id, String password)` : `PUT /users/{id}/reset-password` corps
    `{type:"password", value:password, temporary:false}`. Échec → `KeycloakUnavailableException`.
- `…/adminapp/web/UsersController.java` :
  - `+ GET /users/{id}/edit` (`Authentication`, `@PathVariable id`) → `getUser(id)` → modèle `user`,
    `currentUsername` ; catch → `error`. Vue `edit`.
  - `+ POST /users/{id}/edit` (`@RequestParam` email/firstName/lastName + `@RequestParam(defaultValue=
    "false") boolean enabled`) → `updateUser` → `redirect:/users/{id}/edit?updated` ; catch → `?error`.
  - `+ POST /users/{id}/password` (`@RequestParam password`) → `resetPassword` →
    `redirect:/users/{id}/edit?pwd` ; catch → `?error`.
- `…/admin-application/src/main/resources/templates/users.html` : colonne actions `+` lien
  `<a th:href="@{/users/{id}/edit(id=${u.id})}">éditer</a>` (avant le lien « rôles »).
- `…/adminapp/service/KeycloakAdminClientTest.java` : `+` tests — `updateUser` (stub GET `Map` puis
  capture le corps PUT : `email`/`enabled` modifiés, `username` conservé) ; `resetPassword` (capture le
  corps PUT `reset-password` : `temporary=false`, `value`).

## Intégration générateur

- **Aucune modif générateur** : admin-application déjà installé.
- **`TemplateLoaderTest`** : parité **172 → 173** (1 nouveau fichier : `edit.html`).
- Aucun test générateur nouveau.

## Gestion d'erreurs

- **Keycloak indisponible / user introuvable / token refusé** → `KeycloakUnavailableException` → GET :
  `${error}` « Keycloak indisponible. » ; POST : `redirect:…?error`.
- **Succès** : `?updated` (« Utilisateur mis à jour. ») / `?pwd` (« Mot de passe réinitialisé. »).
- **Auto-désactivation** : case « actif » verrouillée à `true` sur sa propre page (garde UI + champ caché).
- **Champs** : `password` `required` ; email/prénom/nom libres (vides tolérés par Keycloak).
- **CSRF** sur les deux formulaires.
- **Préservation** : `updateUser` fusionne sur la représentation existante (GET→PUT) → `username` et
  autres attributs non écrasés.

## Tests & vérification

- **Test embarqué `KeycloakAdminClientTest` étendu** (Mockito, hors-ligne) : `updateUser` (stub GET
  `Map{username,email,…}` → capture corps PUT : `email`/`enabled` mis à jour, `username` conservé) ;
  `resetPassword` (capture corps PUT `reset-password` : `temporary=false`, `value`). Les tests 3a–3c
  restent verts.
- **`TemplateLoaderTest`** parité **173**.
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=false` → `edit.html` présent + liens « éditer » dans `users.html` ; `mvn -pl
    admin-application -am package` du projet généré **compile ET exécute `KeycloakAdminClientTest` vert** ;
    `docker compose config` valide.
  - Runtime (login admin → /users → éditer → modifier/reset réel) : **manuel/optionnel** (stack +
    Keycloak) — noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Édition du `username` (read-only), gestion des rôles (3c), attributs custom / groupes.
- Garde serveur stricte anti-auto-désactivation (UI seulement).

## Fichiers touchés (Phase 4)

**Template (nouveau) :** `admin-application/src/main/resources/templates/edit.html`.

**Template (modifiés) :** `admin-application/src/main/java/.../service/KeycloakAdminClient.java`,
`admin-application/src/main/java/.../web/UsersController.java`,
`admin-application/src/main/resources/templates/users.html`,
`admin-application/src/test/java/.../service/KeycloakAdminClientTest.java`.

**Tests générateur (modifiés) :** `src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java`
(parité 172 → 173).
