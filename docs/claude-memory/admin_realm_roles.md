---
name: admin-realm-roles
description: "Gestion CRUD des rôles Keycloak dans admin-application — createRealmRole, deleteRealmRole, RealmRolesController, realm-roles.html, ROLE_ADMIN protégé"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3db72030-c893-4c1b-bc2c-b2765fe06257
---

Commits `4ee6f42` (feature) + `913fdde` (fix test).

**Ce qui a été ajouté:**
- `KeycloakRole` record : champ `description` ajouté (3e composant) — casse les instanciations existantes à 2 args.
- `KeycloakAdminClient.createRealmRole(name, description)` — POST `/admin/realms/{realm}/roles`, lève `RoleConflictException` sur HTTP 409.
- `KeycloakAdminClient.deleteRealmRole(name)` — DELETE `/admin/realms/{realm}/roles/{name}`.
- `KeycloakAdminClient.RoleConflictException` — nouvelle inner class.
- `RealmRolesController` — GET `/roles` (liste), POST `/roles` (créer), POST `/roles/{name}/delete` ; `ROLE_ADMIN` protégé contre la suppression (redirect `?error=protected`).
- `realm-roles.html` — table Bootstrap avec badges colorés, formulaire création inline.
- `layout.html` + `home.html` — lien et card "Rôles" ajoutés.

**Piège : ajout de `description` dans KeycloakRole**
Le record `KeycloakRole(String id, String name)` est devenu `KeycloakRole(String id, String name, String description)`. Toutes les instanciations dans les tests (`KeycloakAdminClientTest`) ont cassé — 8 occurrences à passer à `new KeycloakRole("id", "name", null)`.

**Piège : plateforme générée avant le fix**
Le fix au test template n'est visible que dans une plateforme **regénérée** après rebuild du générateur. Une plateforme déjà extraite dans `/tmp/DemoZip/` ne se met pas à jour automatiquement — il faut `pkill` l'ancien serveur, `mvn clean package`, relancer, et regénérer le ZIP.

**How to apply:** quand on ajoute un champ à un record DTO partagé entre prod et test, toujours grep les usages dans les fichiers de test du template avant de committer.
