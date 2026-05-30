# Phase 3b — `admin-application` : création / suppression d'utilisateurs

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** étendre `admin-application` (3a) pour **créer** et **supprimer** des utilisateurs Keycloak.
Gestion des rôles (3c) hors périmètre.

## Contexte

`admin-application` (Phase 3a) est un module toujours installé, BFF réservé `ROLE_ADMIN`, qui liste les
utilisateurs du realm `ms-realm` via `KeycloakAdminClient` (token admin master `admin-cli`, RestTemplate
brut). La page `/users` affiche une table read-only. Keycloak Admin REST API : créer un user =
`POST /admin/realms/{realm}/users` (représentation + `credentials`), supprimer =
`DELETE /admin/realms/{realm}/users/{id}`.

## Décisions de design (validées)

### A. Mot de passe permanent à la création

L'admin saisit un mot de passe utilisable immédiatement : `credentials:[{type:"password", value,
temporary:false}]` (comme les test users du realm). L'utilisateur créé peut se connecter directement.

### B. Suppression : bouton par ligne + garde anti-auto-suppression + confirmation

Un bouton « Supprimer » par ligne, **sauf sur la ligne de l'utilisateur connecté** (garde UI contre le
lockout : `u.username != currentUsername`). `confirm()` JS avant envoi. `POST /users/{id}/delete` (CSRF).

### C. POST-redirect-GET, aucun nouveau fichier, aucun changement générateur

Toutes les actions redirigent vers `/users`. **Uniquement des modifications de fichiers 3a** → parité
`TemplateLoaderTest` **inchangée (169)**. `admin-application` est déjà installé → **aucune modification
du générateur**.

## Architecture & flux

1. **Créer** : `POST /users` (`username, email, firstName, lastName, password`, CSRF) →
   `KeycloakAdminClient.createUser(...)` → `POST /admin/realms/{realm}/users` avec
   `{username, email, firstName, lastName, enabled:true, credentials:[{type:"password", value, temporary:false}]}`
   → **201** → `redirect:/users` ; **409** (username pris) → `redirect:/users?error=conflict` ;
   indisponible → `redirect:/users?error`.
2. **Supprimer** : bouton rendu seulement si `u.username != currentUsername` ; `POST /users/{id}/delete`
   (CSRF, `confirm()`) → `KeycloakAdminClient.deleteUser(id)` → `DELETE /admin/realms/{realm}/users/{id}`
   → `redirect:/users` ; indisponible → `redirect:/users?error`.

Sécurité : toute l'app est déjà `hasRole("ADMIN")` (3a) ; garde anti-auto-suppression au niveau UI ; mot
de passe transmis uniquement côté serveur vers Keycloak.

## Composants (fichiers 3a modifiés — aucun nouveau)

- **`service/KeycloakAdminClient.java`** :
  - `+ createUser(String username, String email, String firstName, String lastName, String password)` :
    `POST {internalUrl}/admin/realms/{realm}/users` (Bearer admin) avec le corps JSON
    `{username, email, firstName, lastName, enabled:true, credentials:[{type:"password", value:password, temporary:false}]}`
    (construit via `Map`/`List`). **201** → OK ; **409** → `UserConflictException` ; autre →
    `KeycloakUnavailableException`.
  - `+ deleteUser(String id)` : `DELETE {internalUrl}/admin/realms/{realm}/users/{id}` (Bearer). Échec →
    `KeycloakUnavailableException`.
  - `+ public static class UserConflictException extends RuntimeException`.
- **`web/UsersController.java`** :
  - `GET /users` : ajoute `currentUsername` (`authentication.getName()`) au modèle (en plus de `users`).
  - `+ POST /users` (`@RequestParam` username/email/firstName/lastName/password) → `createUser` →
    `redirect:/users` ; catch `UserConflictException` → `redirect:/users?error=conflict` ; catch
    `KeycloakUnavailableException` → `redirect:/users?error`.
  - `+ POST /users/{id}/delete` (`@PathVariable id`) → `deleteUser` → `redirect:/users` ; catch
    `KeycloakUnavailableException` → `redirect:/users?error`.
- **`templates/users.html`** :
  - Bandeaux d'erreur : `${param.error == 'conflict'}` → « Nom d'utilisateur déjà pris. » ; `${param.error}`
    (autre) → « Keycloak indisponible. ». (Conserve le `${error}` serveur de 3a pour l'échec de listing.)
  - Colonne « actions » : `<form th:if="${u.username != currentUsername}"
    th:action="@{/users/{id}/delete(id=${u.id})}" method="post"
    onsubmit="return confirm('Supprimer cet utilisateur ?')">` + CSRF + bouton Supprimer.
  - Section « Créer un utilisateur » : `<form th:action="@{/users}" method="post">` + CSRF + champs
    `username`/`email`/`firstName`/`lastName`/`password` (`required` sur username + password) + bouton Créer.
- **`src/test/java/.../service/KeycloakAdminClientTest.java`** : `+` tests embarqués — `createUser` succès
  (URL `/users` + corps contenant `temporary` ⇒ false), `createUser` 409 → `UserConflictException`,
  `deleteUser` succès (DELETE sur `/users/{id}`). Les tests 3a (token + listUsers) restent verts.

## Intégration générateur

- **Aucune modif générateur** : `admin-application` déjà installé (3a). Pas de `CrossCuttingConfigProcessor`,
  pas de route/compose.
- **`TemplateLoaderTest`** : parité **inchangée (169)** — aucun fichier ajouté (fichiers 3a modifiés).
- Aucun test générateur nouveau.

## Gestion d'erreurs

- **Création — 409 (username pris)** → `?error=conflict` → « Nom d'utilisateur déjà pris. ».
- **Création / suppression — Keycloak indisponible / token refusé** → `?error` → « Keycloak indisponible. ».
- **Listing indisponible** (3a, inchangé) → `${error}` « Keycloak indisponible. ».
- **Auto-suppression** : bouton absent sur sa propre ligne (garde UI).
- **Champs manquants** : `required` HTML sur username + password ; un appel incomplet → erreur Keycloak →
  `?error`.
- **CSRF** activé (formulaires create/delete portent le jeton).

## Tests & vérification

- **Test embarqué `KeycloakAdminClientTest` étendu** (Mockito, hors-ligne) : `createUser` (201 + corps
  `temporary=false`), `createUser` conflit (409 → `UserConflictException`), `deleteUser` (DELETE). Les
  tests 3a restent verts (5 tests au total).
- **`TemplateLoaderTest`** parité **169** (inchangée).
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=false` (admin-application toujours présent) → `mvn -pl admin-application -am package` du
    projet généré **compile ET exécute `KeycloakAdminClientTest` (5 tests) vert** ; `docker compose config`
    valide ; `users.html` généré contient le formulaire de création + le bouton supprimer conditionnel.
  - Runtime (login admin → créer/supprimer un utilisateur réel) : **manuel/optionnel** (stack + Keycloak)
    — noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Édition d'utilisateur, reset password, activation/désactivation, recherche/pagination.
- Gestion des rôles (3c).
- Garde serveur stricte anti-auto-suppression (UI seulement ; l'API admin reste capable).

## Fichiers touchés (Phase 3b)

**Template (modifiés, aucun nouveau) :**
`admin-application/src/main/java/.../service/KeycloakAdminClient.java`,
`admin-application/src/main/java/.../web/UsersController.java`,
`admin-application/src/main/resources/templates/users.html`,
`admin-application/src/test/java/.../service/KeycloakAdminClientTest.java`.

**Générateur :** aucun changement. `TemplateLoaderTest` inchangé (169).
