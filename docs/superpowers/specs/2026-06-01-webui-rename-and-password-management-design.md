# Renommage `ms-client` → `ms-webui` + gestion des mots de passe

**Date :** 2026-06-01
**Statut :** validé (design)

## Contexte

Le générateur produit un module UI Thymeleaf nommé `ms-client`. Ce nom prête à
confusion avec une éventuelle entité métier `Client`. On le renomme en `ms-webui`,
plus explicite. Par ailleurs, la page « Mon compte » de `ms-webui` ne propose
aujourd'hui qu'un lien vers Keycloak ; on veut un vrai formulaire de changement de
mot de passe (self-service). Enfin, on veut qu'un ADMIN puisse **forcer** un nouveau
mot de passe lors de la modification d'un utilisateur.

> **Découverte (post-spec initiale) :** l'administration des utilisateurs **existe
> déjà** dans le module `admin-application` (toujours installé) : `UsersController` +
> `users.html` + `edit.html`, avec liste paginée/recherche, création, suppression,
> édition (email/prénom/nom/actif) et **réinitialisation du mot de passe** via
> `KeycloakAdminClient` (token admin **master** `admin-cli`). La partie « admin force
> le mot de passe » est donc à ~95 % déjà là ; il ne manque que le 2ᵉ champ « retaper ».
> Le « socle commun / rôles `realm-management` sur `ms-gateway` » envisagé initialement
> est **abandonné** : on réutilise les creds admin master déjà en place.

Le travail est découpé en **2 lots indépendants et vérifiables séparément** :

- **Lot 1** — renommage `ms-client` → `ms-webui` (mécanique, transversal).
- **Lot 2** — changement de mot de passe : self-service dans `ms-webui` (nouveau) +
  confirmation à 2 champs sur le reset admin existant d'`admin-application`.

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

Deux parties indépendantes. **Aucun changement de realm**, aucun rôle
`realm-management` : on réutilise l'accès admin **master** déjà éprouvé dans
`admin-application` (`KeycloakAdminClient` via `admin-cli`, grant password,
variables `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`).

### A. Mon compte — changer son mot de passe (nouveau, `ms-webui` + `ms-auth`)

La page « Mon compte » de `ms-webui` ne fait aujourd'hui que pointer vers Keycloak.
On ajoute un vrai formulaire self-service. La logique backend vit dans **`ms-auth`**
(pas dans `ms-webui`) pour garder les creds admin hors de l'app user-facing.

**Backend (`ms-auth`)** — nouvel endpoint `POST /auth/account/password` (authentifié) :
- Corps : `{ "oldPassword": …, "newPassword": … }` (DTO `ChangePasswordRequest`).
- Identité lue dans le **JWT validé** : `preferred_username` et `sub` (= id Keycloak).
- Étapes :
  1. **Vérifie l'ancien mot de passe** via un *password grant* (`client_id=ms-gateway`,
     `username=preferred_username`, `password=oldPassword`). Échec → **HTTP 422**
     (code distinct du 401, voir piège ci-dessous).
  2. **Pose le nouveau** via l'API Admin Keycloak `PUT /admin/realms/{realm}/users/{sub}/reset-password`
     avec `{"type":"password","value":newPassword,"temporary":false}`, en utilisant un
     **token admin master** (mêmes creds qu'`admin-application`).
  3. Retour **204** si ok.
- `SecurityConfig` : `/auth/account/**` tombe déjà sous `anyRequest().authenticated()`
  (aucune règle de rôle, aucun `JwtAuthenticationConverter` nécessaire — pas de `hasRole`).
- Helper admin : un petit `KeycloakAdminClient` (ou méthode dans `AuthService`) reprenant
  le pattern `adminToken()` (grant `admin-cli` sur le realm `master`) + `resetPassword(id, pwd)`
  d'`admin-application`. Nouvelles props `keycloak.admin-username`/`admin-password` dans
  l'`application.yml` de `ms-auth` + env `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` dans
  le bloc `ms-auth` de `docker-compose.yml`.

⚠️ **Piège 401 vs business-error :** un « ancien mot de passe faux » **ne doit pas**
renvoyer 401, sinon le `GatewayClient` de `ms-webui` interprète 401 = token expiré,
tente un refresh + rejeu, puis déconnecte l'utilisateur. → ms-auth renvoie **422**
pour cette erreur métier, et `ms-webui` n'utilise **pas** `GatewayClient` pour cet
appel (voir frontend).

**Frontend (`ms-webui` / `account.html` + `AccountController` + `MsAuthClient`)**
- Remplacer le lien Keycloak par un formulaire à 3 champs (CSRF inclus comme les autres
  formulaires) :
  - *Ancien mot de passe*
  - *Nouveau mot de passe* (aucune restriction ; peut être identique à l'ancien)
  - *Retaper le nouveau mot de passe* (doit correspondre)
- Validation **client** (JS : les deux nouveaux champs correspondent) **et serveur**
  (`AccountController` recompare avant l'appel).
- `AccountController.POST /account/password` : lit `oldPassword`/`newPassword`/`confirm`,
  vérifie `newPassword == confirm` (sinon rouge « les mots de passe ne correspondent pas »),
  puis appelle **`MsAuthClient.changePassword(accessToken, old, new)`** — méthode dédiée
  (pas `GatewayClient`) qui POST `gateway/auth/account/password` avec le Bearer de session :
  - 204 → succès ; 422 → `WrongOldPasswordException` ; 401 → refresh une fois via
    `MsAuthClient.refresh(refreshToken)` + maj session + rejeu ; autre → `AuthUnavailableException`.
- Affichage : alerte **verte** « Mot de passe modifié » si succès ; **rouge** sinon
  (ancien faux / non-correspondance / indisponible). On reste sur `/account`.

### B. Forcer un mot de passe côté ADMIN — confirmation à 2 champs (`admin-application`)

L'admin users **existe déjà** (`UsersController` + `edit.html` + `KeycloakAdminClient.resetPassword`,
`temporary:false`). La carte « Réinitialiser le mot de passe » d'`edit.html` n'a qu'un
champ. **Seul ajout** : un 2ᵉ champ « Retaper » + validation de correspondance.

- `edit.html` : ajouter le champ *Retaper le nouveau mot de passe* dans la carte de reset,
  + un petit script JS qui bloque la soumission si les deux champs diffèrent (message rouge).
- `UsersController.resetPassword(id, password, confirm)` : garde serveur — si
  `password != confirm`, redirige vers `…/edit?error=mismatch` (et `edit.html` affiche
  l'alerte rouge correspondante) ; sinon `resetPassword` existant inchangé (vert `?pwd`).
- Aucune autre modification : pas de nouvelle page, pas de nouvel endpoint, pas de realm.

### Décisions retenues

1. Mot de passe forcé par l'ADMIN = **permanent** (`temporary:false`, déjà le cas).
2. Le nouveau mot de passe (self ou admin) n'a **aucune restriction** de complexité ;
   seule la correspondance saisie/retape est exigée.
3. Accès Keycloak Admin = **creds admin master** (réutilisés dans `ms-auth`, déjà en
   place dans `admin-application`) — pas de service account `realm-management`.

### Tests Lot 2

- **`ms-auth`** : test du service de changement self (mock `RestTemplate`) — succès
  (grant ok → reset appelé), mauvais ancien mot de passe (grant 401 → 422, reset **non**
  appelé). Modèle : `admin-application/.../KeycloakAdminClientTest`.
- **`ms-webui`** : test `AccountController` (modèle `ChatControllerTest`) — non-correspondance
  → rouge sans appel backend ; `MsAuthClient.changePassword` mappe 422 → erreur, 204 → succès.
- **`admin-application`** : test `UsersController.resetPassword` — `password != confirm`
  → redirection `error=mismatch` sans appel `resetPassword` ; égal → appel effectué.

### Vérification Lot 2

- Génération avec `webUI: true` + `resources[]` non vide ; plateforme démarrée après
  `clean-docker.sh`.
- **Self** (`ms-webui` `/account`) : connexion utilisateur, changement réussi (vert),
  reconnexion avec le nouveau mot de passe ; ancien faux → rouge ; non-correspondance → rouge.
- **Admin** (`admin-application` `/users/{id}/edit`) : reset avec 2 champs correspondants
  → vert, l'utilisateur ciblé se connecte avec le nouveau mot de passe ; champs différents → rouge.

---

## Hors périmètre (YAGNI)

- Refonte de l'admin users d'`admin-application` (déjà fonctionnel : liste/recherche,
  création, suppression, édition, reset) — on n'y touche que pour le 2ᵉ champ de confirmation.
- Création d'une section admin users dans `ms-webui` (redondant avec `admin-application`).
- Service account `realm-management` sur `ms-gateway` (remplacé par les creds admin master).
- Politique de complexité des mots de passe.
- Mot de passe temporaire imposé à la 1ʳᵉ connexion (alternative documentée, non retenue).
