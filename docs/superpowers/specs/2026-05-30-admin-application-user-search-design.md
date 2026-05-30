# Phase 5 — `admin-application` : recherche + pagination des utilisateurs

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** ajouter recherche + pagination (20/page) à la page `/users` d'`admin-application`, via les
params Keycloak `search`/`first`/`max` et l'endpoint `/users/count`. (Hors roadmap initiale — suite
demandée par l'utilisateur.)

## Contexte

`admin-application` (3a–4) liste/crée/supprime/édite des utilisateurs et gère leurs rôles via
`KeycloakAdminClient` (token admin master `admin-cli`, RestTemplate brut). `listUsers()` fait aujourd'hui
`GET /admin/realms/{realm}/users?max=100` (cappé à 100, sans recherche). Keycloak Admin API supporte la
recherche/pagination : `GET /users?first&max&search` et `GET /users/count?search`.

## Décisions de design (validées)

### A. Pagination basée sur le total (count)

`countUsers(search)` (`GET /users/count`) fournit le total → affichage « page X / N », boutons
Précédent/Suivant désactivés aux bornes. Précis. Coût : un appel `count` par chargement.

### B. Page size fixe = 20 ; recherche via `search` Keycloak

Taille de page fixe (20). Une seule barre de recherche → param Keycloak `search` (substring sur
username/email/firstName/lastName).

### C. Aucun nouveau fichier, aucun changement générateur

Modifs de fichiers existants uniquement → parité `TemplateLoaderTest` **173 inchangée**.
admin-application déjà installé → **aucune modification du générateur**.

## Architecture & flux

1. `GET /users?search={q}&page={n}` → `UsersController` : `size=20`, `first=n*size` → `listUsers(q, first,
   size)` (page) + `countUsers(q)` (total) → modèle `users`, `search`, `page`, `totalPages`, `hasPrev`,
   `hasNext`.
2. Vue : **form GET** de recherche (input `search` pré-rempli) ; table ; **pager** (« ← Précédent » /
   « page X / N » / « Suivant → ») dont les liens conservent `search`.

**API Keycloak (RestTemplate, token admin master) :**
- `GET /admin/realms/{realm}/users?first={first}&max={max}[&search={q}]` (UriComponentsBuilder,
  **URL-encodé**).
- `GET /admin/realms/{realm}/users/count[?search={q}]` → entier (total).

`listUsers()` → `listUsers(String search, int first, int max)` (un seul appelant : `UsersController` ;
les 2 tests existants adaptés). `+ countUsers(String search)`.

## Composants (fichiers existants modifiés — aucun nouveau)

- **`…/adminapp/service/KeycloakAdminClient.java`** :
  - `listUsers()` → **`listUsers(String search, int first, int max)`** : URL via
    `UriComponentsBuilder.fromUriString(internalUrl + "/admin/realms/" + realm + "/users")
    .queryParam("first", first).queryParam("max", max)` + `queryParam("search", search)` si non-blank,
    puis `.encode().toUriString()`. Reste identique (Bearer, `KeycloakUser[]`, catches).
  - `+ countUsers(String search)` : `GET .../users/count[?search=]` → `Integer` (0 si null). Échec →
    `KeycloakUnavailableException`.
  - `+ import org.springframework.web.util.UriComponentsBuilder;`
- **`…/adminapp/web/UsersController.java`** — `users(...)` :
  - `@RequestParam(required = false) String search`, `@RequestParam(defaultValue = "0") int page` ;
    `size = 20` ; `first = page*size`.
  - modèle : `currentUsername`, `search` (`""` si null), `page` ; dans le try :
    `users = listUsers(search, first, size)`, `total = countUsers(search)`,
    `totalPages = total==0?1:(int)Math.ceil((double)total/size)`, `hasPrev = page>0`,
    `hasNext = (page+1)<totalPages` ; catch `KeycloakUnavailableException` → `error`.
- **`src/main/resources/templates/users.html`** :
  - **form GET** de recherche (`<form th:action="@{/users}" method="get">` + `<input name="search"
    th:value="${search}">` + bouton) au-dessus de la table.
  - **pager** sous la table (`th:if="${users}"`) : `← Précédent` (`th:if="${hasPrev}"`,
    `@{/users(search=${search}, page=${page-1})}`), « Page `${page+1}` / `${totalPages}` », `Suivant →`
    (`th:if="${hasNext}"`, `page+1`).
  - table + formulaire de création + colonne actions (éditer/rôles/supprimer) **inchangés**.
- **`…/adminapp/service/KeycloakAdminClientTest.java`** :
  - Adapter les 2 tests `listUsers` existants à `listUsers(null, 0, 20)` (matcher
    `contains("/admin/realms/ms-realm/users")` reste valide).
  - `+ list_users_builds_url_with_paging_and_search` : `ArgumentCaptor<String>` sur l'URL du GET → assert
    contient `first=20`, `max=20`, `search=bob`.
  - `+ count_users_returns_total` : stub `/users/count` → `42` → `countUsers("")==42`.

## Intégration générateur

- **Aucune modif générateur** ; **`TemplateLoaderTest` parité 173 inchangée** (aucun fichier ajouté).
- Aucun test générateur nouveau.

## Gestion d'erreurs

- **Keycloak indisponible / count ou list en échec** → `KeycloakUnavailableException` → `${error}`
  « Keycloak indisponible. » ; table + pager masqués (`th:if="${users}"`).
- **Recherche vide** (`search` null/blank) → param omis → Keycloak renvoie tout (paginé).
- **Page hors bornes** (URL forgée `?page=999`) → Keycloak renvoie une page vide ; `hasNext=false`,
  `hasPrev=true` ; table vide, pager cohérent, pas d'erreur.
- **URL/injection** : `search` et les params sont **URL-encodés** par `UriComponentsBuilder.encode()`.
- **CSRF** : le form de recherche est en `GET` (pas de CSRF requis) ; les forms de mutation
  (create/delete) restent inchangés.

## Tests & vérification

- **Test embarqué `KeycloakAdminClientTest`** : 2 tests `listUsers` adaptés ;
  `+ list_users_builds_url_with_paging_and_search` (URL contient `first`/`max`/`search`) ;
  `+ count_users_returns_total`. Les autres (create/delete/roles/update/reset) inchangés.
- **`TemplateLoaderTest`** parité **173** (inchangée).
- **Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
  `pkill` et lancement en commandes séparées ; sandbox désactivé) :
  - `clientWebUI=false` → `users.html` généré contient le form de recherche + le pager ;
    `listUsers`/`countUsers` présents ; `mvn -pl admin-application -am package` du projet généré
    **compile ET exécute `KeycloakAdminClientTest` vert** ; `docker compose config` valide.
  - Runtime (login admin → `/users` → recherche + navigation pages réelle) : **manuel/optionnel**
    (stack + Keycloak) — noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Tri par colonne, taille de page configurable (fixe à 20), recherche multi-champs séparés.
- Pagination de la liste des rôles / de l'historique chat (autres modules).

## Fichiers touchés (Phase 5)

**Template (modifiés, aucun nouveau) :** `admin-application/src/main/java/.../service/KeycloakAdminClient.java`,
`admin-application/src/main/java/.../web/UsersController.java`,
`admin-application/src/main/resources/templates/users.html`,
`admin-application/src/test/java/.../service/KeycloakAdminClientTest.java`.

**Générateur :** aucun changement. `TemplateLoaderTest` inchangé (173).
