---
name: admin-application-ui-bootstrap
description: "admin-application UI redesign — Bootstrap 5.3 dark theme identique ms-client, navbar avec sec:authentication, self-row protégé, roles.html 2 cards côte à côte"
metadata: 
  node_type: memory
  type: project
  originSessionId: 3db72030-c893-4c1b-bc2c-b2765fe06257
---

Commit `564c275`: refonte complète de admin-application avec le même thème Bootstrap 5.3 dark que ms-client.

**Fichiers modifiés:** `app.css`, `layout.html`, `login.html`, `home.html`, `users.html`, `edit.html`, `roles.html`, `pom.xml`.

**Points notables:**

- `app.css` — copie exacte du thème ms-client + `.self-row` adapté (`color: #6b7280 !important` et `pointer-events: none`) pour la protection ligne admin courant dans users.html.
- `layout.html` — fragments `head(title)` + `header` (même pattern que ms-client). Username affiché via `sec:authentication="name"` (requiert `thymeleaf-extras-springsecurity6` ajouté au pom.xml).
- `users.html` — badges actif/inactif colorés inline (vert `#10b981` / rouge `#ef4444`), self-row grisé protégé. Formulaire de création en card Bootstrap avec layout `row g-3`.
- `edit.html` — layout 2 colonnes : édition à gauche, reset mot de passe à droite. `form-check` Bootstrap pour la checkbox "Actif".
- `roles.html` — 2 cards côte à côte : rôles actuels (bouton "Retirer") et assignables (bouton "Assigner"). Badges violet pour rôles normaux, cyan pour ADMIN. Mention "protégé" sur la propre ligne de l'admin (au lieu d'un bouton disabled).

**Dépendance:** `thymeleaf-extras-springsecurity6` ajouté au pom.xml d'admin-application (même raison que ms-client — `sec:authentication` dans le layout fragment).

Related: [[ms-client-ui-bootstrap]]
