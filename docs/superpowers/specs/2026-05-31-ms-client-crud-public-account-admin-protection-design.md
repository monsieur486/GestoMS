# Design — ms-client CRUD complet, pages publique/compte, protection admin

**Date:** 2026-05-31
**Statut:** validé section par section

## Périmètre

Cinq évolutions indépendantes sur les templates ms-platform :

1. **Benchmark** — re-auth automatique + fix quoting dans `benchmark-async-batch.sh`
2. **service-a backend** — ajout `GET /{id}`, `PUT /{id}`, `DELETE /{id}`
3. **ms-client CRUD** — update + delete dans ResourceController/UI, GatewayClient étendu
4. **ms-client pages** — landing publique (`/public`) + page "mon compte" (`/account`)
5. **admin-application** — protection serveur : auto-suppression + auto-modification de rôles

---

## A — Benchmark

### A1 — Re-auth automatique

`benchmark-async-batch.sh` source `tokens.env` au démarrage mais le token `TOKEN_BATCH` expire entre deux runs. `tokens.env` ne contient que les tokens (pas les credentials) — la re-auth passe par ms-auth via le gateway.

Fix : au démarrage, après avoir sourcé `tokens.env`, le script ré-authentifie systématiquement l'utilisateur batch via `POST $GATEWAY_URL/auth/login` avec les credentials configurables (`BATCH_USER`, `BATCH_PASSWORD`). Le token obtenu remplace `TOKEN_BATCH` pour ce run. `tokens.env` n'est pas réécrit.

Variables d'environnement avec défauts :
- `GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}`
- `BATCH_USER=${BATCH_USER:-test-batch}`
- `BATCH_PASSWORD=${BATCH_PASSWORD:-user123}`

### A2 — Fix quoting

Ligne 112, remplacer la substitution imbriquée :
```bash
cat "$(ls "$RESULT_DIR"/json/job-*.json | tail -n 1)"
```
par une variable intermédiaire :
```bash
last_job=$(ls "$RESULT_DIR"/json/job-*.json | tail -n 1)
cat "$last_job"
```

**Fichiers touchés :** `src/main/resources/templates/ms-platform/benchmark-async-batch.sh`

---

## B — service-a : nouveaux endpoints REST

### Nouveaux endpoints

| Méthode | Route                       | Autorisation                                  | Réponse            |
|---------|-----------------------------|-----------------------------------------------|--------------------|
| GET     | `/api/resources-a/{id}`     | `hasRole('ADMIN') or hasRole('USER_SERVICE_A')` | `ResourceADto` / 404 |
| PUT     | `/api/resources-a/{id}`     | `hasRole('ADMIN') or hasRole('USER_SERVICE_A')` | `ResourceADto` / 404 |
| DELETE  | `/api/resources-a/{id}`     | `hasRole('ADMIN') or hasRole('USER_SERVICE_A')` | 204 No Content / 404 |

### Service

`ResourceAService` :
- `findById(Long id)` → `ResourceADto` (throws `ResourceNotFoundException` si absent)
- `update(Long id, ResourceADto dto)` → findById + mise à jour name/description + save
- `delete(Long id)` → findById (pour 404 propre) + deleteById

`ResourceNotFoundException` annotée `@ResponseStatus(HttpStatus.NOT_FOUND)`.

### Portée générateur

Le template `service-a` est la source. Les templates `service-b` et `service-c` sont clonés par le générateur depuis `service-a` (renommage du className) — ils héritent automatiquement de ces endpoints.

**Fichiers touchés :** `service-a/src/main/java/.../controller/ResourceAController.java`,
`.../service/ResourceAService.java` (+ exception `ResourceNotFoundException.java` nouvelle).

---

## C — ms-client : CRUD complet (update + delete)

### C1 — GatewayClient

Deux nouvelles méthodes publiques, même pattern `exchangeWithRefresh` que `get`/`post` :

```java
public String put(HttpSession session, String path, String jsonBody)
public void delete(HttpSession session, String path)
```

`delete` retourne `void` (204 attendu) ; les exceptions existantes (`SessionExpiredException`, `BackendForbiddenException`, `BackendUnavailableException`) s'appliquent identiquement.

### C2 — ResourceController : nouvelles routes

```
GET  /resources/{serviceName}/{id}/edit     → formulaire pré-rempli
POST /resources/{serviceName}/{id}/edit     → PUT backend → redirect GET list
POST /resources/{serviceName}/{id}/delete   → DELETE backend → redirect GET list
```

Flux GET edit : `gatewayClient.get(session, "/{serviceName}{routePrefix}/{id}")` → désérialise `Map<String,Object>` → injecte dans le model → retourne `resource-edit`.

Gestion d'erreurs cohérente avec l'existant :
- Session expirée → `redirect:/login?expired`
- Backend indisponible / 404 → `redirect:/resources/{serviceName}?error`
- Succès → `redirect:/resources/{serviceName}`

### C3 — Templates UI

**`resource.html`** : chaque ligne de la table ajoute deux actions :
- Lien vers `/resources/{serviceName}/{id}/edit` (texte "Modifier")
- Formulaire POST vers `/resources/{serviceName}/{id}/delete` avec `confirm('Supprimer ?')`  côté JS et token CSRF

**`resource-edit.html`** (nouveau fichier) : formulaire avec champs `name` et `description` pré-remplis, action = `POST /resources/{serviceName}/{id}/edit`, bouton "Enregistrer" + lien "Annuler" vers `GET /resources/{serviceName}`. Messages d'erreur `${param.error}`.

**Fichiers touchés :** `ms-client/.../service/GatewayClient.java`,
`ms-client/.../web/ResourceController.java`,
`ms-client/src/main/resources/templates/resource.html`,
`ms-client/src/main/resources/templates/resource-edit.html` (nouveau).

---

## D — ms-client : pages publique et "mon compte"

### D1 — Page publique `/public`

Route `GET /public` — accessible sans authentification.

**`SecurityConfig`** : ajouter `/public` dans `.requestMatchers("/login", "/public", "/css/**", "/actuator/health").permitAll()`.

**`PublicController`** (nouveau) : `@GetMapping("/public")` → retourne `"public"`. Pas de `Authentication` requise.

**`public.html`** (nouveau) : landing statique — nom de la plateforme (`ms-platform`), courte description générique, bouton/lien "Se connecter" vers `/login`. Pas d'état dynamique.

### D2 — Page "mon compte" `/account`

Route `GET /account` — authentification requise (couvert par `anyRequest().authenticated()`).

**`ClientProperties`** étendu : champ `keycloakAccountUrl` (type `String`).

```java
@ConfigurationProperties(prefix = "client")
public record ClientProperties(List<ResourceEntry> resources, String keycloakAccountUrl) { … }
```

**`application.yml`** : nouvelle entrée `client.keycloak-account-url: http://localhost:8080/realms/ms-platform/account` (valeur par défaut template).

**`AccountController`** (nouveau) : injecte `Authentication` + `ClientProperties`. Expose `username`, `roles`, `keycloakAccountUrl` au model → retourne `"account"`.

**`account.html`** (nouveau) : affiche username, liste des rôles, lien "Changer mon mot de passe" (`href="${keycloakAccountUrl}"`, target `_blank`).

**`layout.html`** : ajouter lien "Mon compte" → `/account` dans le header (visible pour tout utilisateur authentifié).

**Fichiers touchés :** `ms-client/.../configuration/SecurityConfig.java`,
`ms-client/.../config/ClientProperties.java`,
`ms-client/src/main/resources/application.yml`,
`ms-client/.../web/AccountController.java` (nouveau),
`ms-client/src/main/resources/templates/account.html` (nouveau),
`ms-client/src/main/resources/templates/public.html` (nouveau),
`ms-client/src/main/resources/templates/layout.html`.

---

## E — admin-application : protection serveur auto-actions

### E1 — Règle métier

Un ADMIN ne peut pas :
- Se supprimer lui-même (`POST /users/{id}/delete`)
- Ajouter ou retirer N'IMPORTE QUEL rôle à lui-même (`POST /users/{id}/roles/add`, `/roles/remove`)

Seul un autre ADMIN peut effectuer ces actions sur un ADMIN donné.

### E2 — UsersController.delete()

Ajouter `Authentication authentication` en paramètre.

```java
@PostMapping("/users/{id}/delete")
public String delete(@PathVariable String id, Authentication authentication) {
    try {
        KeycloakUser user = keycloakAdminClient.getUser(id);
        if (user != null && user.username().equals(authentication.getName())) {
            return "redirect:/users?error=self";
        }
        keycloakAdminClient.deleteUser(id);
        return "redirect:/users";
    } catch (KeycloakAdminClient.KeycloakUnavailableException e) {
        return "redirect:/users?error";
    }
}
```

**`users.html`** : ajouter message pour `param.error[0] == 'self'` : "Vous ne pouvez pas supprimer votre propre compte."

### E3 — RolesController.add() et remove()

Ajouter `Authentication authentication` dans `add()` et `remove()`. Dans chacun, fetcher `keycloakAdminClient.getUser(id)` et comparer le username :

```java
if (user != null && user.username().equals(authentication.getName())) {
    return "redirect:/users/" + id + "/roles?error=self";
}
```

**`roles.html`** : 
- Bouton "Retirer" : masquer si `user.username == currentUsername` (étendre la condition existante de `r.name == 'ADMIN'` à `user.username == currentUsername` simplement).
- Bouton "Assigner" : masquer si `user.username == currentUsername`.
- Message d'erreur pour `param.error[0] == 'self'`.

### E4 — Défense en profondeur

La protection HTML (masquage des boutons) reste présente pour l'UX. La protection serveur (vérification username) est le vrai garde-fou. Les deux couches sont indépendantes.

**Fichiers touchés :** `admin-application/.../web/UsersController.java`,
`admin-application/.../web/RolesController.java`,
`admin-application/src/main/resources/templates/users.html`,
`admin-application/src/main/resources/templates/roles.html`.

---

## Tests embarqués

- **service-a** : tests unitaires pour `ResourceAService.update()` (found, not found) et `delete()` (found, not found).
- **ms-client GatewayClient** : étendre `GatewayClientTest` avec PUT (succès, 401→refresh→retry) et DELETE (succès, 401→refresh→retry).
- **admin-application** : pas de nouveau test unitaire (logique triviale `equals`). La protection est vérifiable manuellement.

## Fichiers impactés — récapitulatif

| Fichier | Nature |
|---------|--------|
| `benchmark-async-batch.sh` | modifié |
| `service-a/.../controller/ResourceAController.java` | modifié |
| `service-a/.../service/ResourceAService.java` | modifié |
| `service-a/.../service/ResourceNotFoundException.java` | nouveau |
| `ms-client/.../service/GatewayClient.java` | modifié |
| `ms-client/.../web/ResourceController.java` | modifié |
| `ms-client/.../web/AccountController.java` | nouveau |
| `ms-client/.../web/PublicController.java` | nouveau |
| `ms-client/.../config/ClientProperties.java` | modifié |
| `ms-client/.../configuration/SecurityConfig.java` | modifié |
| `ms-client/src/main/resources/application.yml` | modifié |
| `ms-client/src/main/resources/templates/resource.html` | modifié |
| `ms-client/src/main/resources/templates/resource-edit.html` | nouveau |
| `ms-client/src/main/resources/templates/account.html` | nouveau |
| `ms-client/src/main/resources/templates/public.html` | nouveau |
| `ms-client/src/main/resources/templates/layout.html` | modifié |
| `admin-application/.../web/UsersController.java` | modifié |
| `admin-application/.../web/RolesController.java` | modifié |
| `admin-application/src/main/resources/templates/users.html` | modifié |
| `admin-application/src/main/resources/templates/roles.html` | modifié |
