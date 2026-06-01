# Renommage `ms-client` → `ms-webui` + gestion des mots de passe

**Date :** 2026-06-01
**Statut :** validé (design)

## Contexte

Le générateur produit un module UI Thymeleaf nommé `ms-client`. Ce nom prête à
confusion avec une éventuelle entité métier `Client`. On le renomme en `ms-webui`,
plus explicite. Par ailleurs, la page « Mon compte » ne propose aujourd'hui qu'un
lien vers Keycloak ; on veut un vrai formulaire de changement de mot de passe, et
une nouvelle section d'administration des utilisateurs où un ADMIN peut forcer un
nouveau mot de passe.

Le travail est découpé en **2 lots indépendants et vérifiables séparément** :

- **Lot 1** — renommage `ms-client` → `ms-webui` (mécanique, transversal).
- **Lot 2** — changement de mot de passe (self + admin), qui s'appuie sur l'API
  Admin Keycloak.

Le Lot 1 est livré et vérifié **avant** d'entamer le Lot 2.

---

## Lot 1 — Renommage `ms-client` → `ms-webui`

### Objectif

Renommage cohérent et isolé, vérifiable par `mvn test` + une génération de plateforme
avec `webUI: true` et un `resources[]` non vide.

### Table de correspondance

| Élément | Avant | Après |
|---|---|---|
| Dossier module (template) | `templates/ms-platform/ms-client/` | `templates/ms-platform/ms-webui/` |
| Package Java | `com.mr486.msplatform.client` | `com.mr486.msplatform.webui` |
| Classe application | `ClientApplication` | `WebUiApplication` |
| Properties | `ClientProperties` (`@ConfigurationProperties("client")`) | `WebUiProperties` (`@ConfigurationProperties("webui")`) |
| Flag (`FeatureOptions`) | `clientWebUI` / `isClientWebUI()` | `webUI` / `isWebUI()` |
| Nom service / Eureka / docker-compose | `ms-client` | `ms-webui` |
| Port HTTP | 8090 | **inchangé (8090)** |

### Fichiers impactés (≈ 36)

- **Module template** (`ms-client/` → `ms-webui/`) : 27 fichiers — renommer le dossier,
  le sous-arbre de package `.../client/` → `.../webui/`, et substituer les occurrences
  `client` (package, classes, bean names, préfixe de config) à l'intérieur.
- **Générateur** (3 fichiers) :
  - `dto/FeatureOptions.java` — champ `clientWebUI` → `webUI`, getter `isWebUI()`.
  - `pipeline/processor/FeatureFilterProcessor.java` — filtre de chemin `ms-client/` → `ms-webui/`, `isClientWebUI()` → `isWebUI()`.
  - `pipeline/processor/CrossCuttingConfigProcessor.java` — `desiredModules` (`ms-client` → `ms-webui`), `blocksToRemove` (compose), `rewriteGatewayYml` (route), `rewriteClientYml` → `rewriteWebUiYml` (bloc `client:` → `webui:` dans l'`application.yml` du module), wait-for `test-all.sh` (`http://localhost:8090/login`), javadoc.
- **Tests** (3 fichiers) : `CrossCuttingConfigProcessorTest`, `FeatureOptionsDeserializationTest`, `FeatureFilterProcessorTest`.
- **Plateforme** : `docker-compose.yml` (bloc service + volume éventuel), `dot-env`, `dist.env`, `static/index.html`, `README.md`.

### Points d'attention

- **Préfixe de config `client:` → `webui:`** : le bloc est réécrit par
  `CrossCuttingConfigProcessor` (catalogue des resources) **et** présent dans
  l'`application.yml` du module. Les deux sources doivent être synchronisées.
- **Flag JSON public** `clientWebUI` → `webUI`. Les payloads existants utilisant
  `clientWebUI` sont **silencieusement ignorés** (`FAIL_ON_UNKNOWN_PROPERTIES=false`),
  donc pas de rupture dure ; le défaut reste `false`.
- **Layout généré** : `GeneratedOutputLayoutTest` reformate sur les vrais templates
  → garder ≤120 colonnes / 4 espaces / un import par ligne / javadoc français.

### Vérification Lot 1

- `mvn test` vert.
- Génération avec `{"features":{"webUI":true},"resources":[…non vide…]}` :
  - le ZIP contient `ms-webui/` (pas `ms-client/`), le module compile/démarre,
  - `docker-compose.yml`, route gateway, `test-all.sh` référencent `ms-webui`,
  - le bloc `webui:` (catalogue resources) est correct.
- Tuer les `java -jar` zombies sur :8080 avant de vérifier (sinon vieux build servi).

---

## Lot 2 — Changement de mot de passe (self + admin)

### Socle commun — accès API Admin Keycloak depuis `ms-auth`

- **Realm** (`keycloak/import/ms-realm-realm.json`) : ajouter au service account du
  client `ms-gateway` les rôles client `realm-management` → `["view-users", "manage-users"]`
  (champ `serviceAccountClientRoles`). `serviceAccountsEnabled` est déjà `true`.
- **`ms-auth`** : nouveau `KeycloakAdminClient` (service) qui obtient un token
  `client_credentials` avec les creds `ms-gateway` existants et appelle l'API Admin :
  - `GET /admin/realms/{realm}/users?max=…` — liste des utilisateurs.
  - `PUT /admin/realms/{realm}/users/{id}/reset-password` — `{"type":"password","value":…,"temporary":false}`.
- **`SecurityConfig` (`ms-auth`)** :
  - ajouter un `JwtAuthenticationConverter` qui mappe `realm_access.roles` → `ROLE_*`,
  - `/auth/admin/**` → `hasRole('ADMIN')`, `/auth/account/**` → authentifié,
  - `/auth/login`, `/auth/refresh`, `/actuator/**` restent ouverts.
- ⚠️ **Gotcha re-import realm** (mémoire `keycloak_realm_reimport`) : un volume
  `keycloak_db_data` obsolète masque les nouveaux rôles SA. Vérifier après
  `clean-docker.sh` (problème d'état, pas de code).
- ⚠️ **Réécriture per-resource du realm** : `CrossCuttingConfigProcessor.rewriteRealm()`
  régénère le realm par resource (ajout des users `test-<service>`). Vérifier que
  `serviceAccountClientRoles` sur `ms-gateway` **survit** à cette réécriture (édition
  d'arbre Jackson, le nœud client ne doit pas être reconstruit à plat).

### A. Mon compte — changer son mot de passe

**Frontend (`ms-webui` / `account.html` + `AccountController`)**
- Remplacer le lien Keycloak par un formulaire à 3 champs :
  - *Ancien mot de passe*
  - *Nouveau mot de passe* (aucune restriction ; peut être identique à l'ancien)
  - *Retaper le nouveau mot de passe* (doit correspondre)
- Validation **client** (les deux nouveaux champs correspondent) **et** serveur.
- `AccountController.POST /account/password` → `GatewayClient.post(...)` →
  `ms-auth POST /auth/account/password {oldPassword, newPassword}`.
- Affichage : alerte **verte** « mot de passe modifié » si 2xx ; **rouge** sinon
  (ancien mot de passe faux, ou mismatch).

**Backend (`ms-auth`)**
- `POST /auth/account/password` (authentifié) :
  1. Vérifie l'ancien mot de passe via *password grant* sur `preferred_username`
     du JWT (401/erreur si faux).
  2. `reset-password` sur le `sub` du JWT, `temporary:false`.
  3. Retour `204` si ok, `400/401` si échec.

### B. Administration des utilisateurs (ADMIN)

**Frontend (`ms-webui`)**
- Nav (`layout.html`) : lien « Utilisateurs » avec `sec:authorize="hasRole('ADMIN')"`.
- `/admin/users` — page liste (table : username, email, activé, bouton « Modifier »)
  alimentée par `ms-auth GET /auth/admin/users`.
- `/admin/users/{id}` — page modification : infos utilisateur (lecture seule pour ce
  périmètre) + formulaire **forcer mot de passe** à **2 champs** (mot de passe +
  retaper, doivent correspondre).
- `UserAdminController` (méthodes protégées par `hasRole('ADMIN')` côté webui) →
  `GatewayClient` → endpoints `ms-auth` ci-dessous.

**Backend (`ms-auth`)**
- `GET /auth/admin/users` (`hasRole('ADMIN')`) → liste `{id, username, email, enabled}`.
- `POST /auth/admin/users/{id}/password` (`hasRole('ADMIN')`) → `{newPassword}` →
  `reset-password` sur `{id}`, `temporary:false`.
- L'autorisation **utilisateur** se fait via le rôle ADMIN du JWT ; l'autorisation
  **Keycloak** via le service account (`client_credentials`). Séparation nette.

### Décisions retenues

1. Mot de passe forcé par l'ADMIN = **permanent** (`temporary:false`). L'utilisateur
   se connecte directement avec le nouveau mot de passe.
2. La liste utilisateurs affiche **tous les utilisateurs du realm** (y compris `test-*`).
3. Le nouveau mot de passe (self ou admin) n'a **aucune restriction** de complexité ;
   seule la correspondance retape/saisie est exigée.

### Tests Lot 2

- **Modules générés** : tests de contrôleur sur le modèle de `ChatControllerTest`
  (`AccountController` : mismatch → erreur ; `UserAdminController` : accès non-ADMIN
  refusé). Côté `ms-auth` : test de mapping des rôles + protection `/auth/admin/**`.
- **Générateur** : garder verts `CrossCuttingConfigProcessorTest` (realm avec
  `serviceAccountClientRoles` préservé après réécriture per-resource), gateway routes,
  et `GeneratedOutputLayoutTest`.

### Vérification Lot 2

- Génération avec `webUI: true` + `resources[]` non vide ; plateforme démarrée après
  `clean-docker.sh`.
- Self : connexion utilisateur, `/account` → changement réussi (vert), nouvelle
  connexion avec le nouveau mot de passe ; ancien faux → rouge.
- Admin : `/admin/users` liste les comptes ; modification d'un user → force un mot de
  passe → vert ; l'utilisateur ciblé se connecte avec le nouveau mot de passe.
- Accès `/admin/users` par un non-ADMIN → refusé.

---

## Hors périmètre (YAGNI)

- CRUD complet utilisateurs (création/suppression, gestion des rôles, email, enabled).
- Politique de complexité des mots de passe.
- Mot de passe temporaire imposé à la 1ʳᵉ connexion (alternative documentée, non retenue).
