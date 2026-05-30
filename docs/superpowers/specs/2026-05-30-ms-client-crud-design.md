# Phase 2c — `ms-client` : CRUD générique (list + create) sur les resources

**Date:** 2026-05-30
**Statut:** spec validé section par section, prêt pour plan d'implémentation
**Périmètre:** ajouter au module `ms-client` une UI générique de **consultation (list)** et **création
(create)** des entités des resource-services, pilotée par un catalogue de resources injecté par le
générateur, avec filtrage d'accès par rôle. Edit/Delete sont hors périmètre (le backend ne les expose
pas). Notifs (2d) et chat (2e) restent hors périmètre.

## Contexte

`ms-client` (Phases 2a/2b) est un BFF Spring Boot MVC : login/logout via ms-auth, session Redis, pages
protégées par Spring Security (rôles realm dans le `SecurityContext`), et `GatewayClient` qui proxifie le
backend via le gateway avec refresh réactif sur 401 (2b).

Faits établis sur les resource-services (template `service-a`, cloné par resource) :
- **Entité uniforme** : DTO `{ id, name, description }` pour TOUS les resources (le `className` ne change
  que les noms, pas les champs).
- **Contrat REST limité** : le controller expose seulement `GET <routePrefix>` (findAll) et
  `POST <routePrefix>` (create). **Pas de get-by-id, update, ni delete.**
- Accès backend : `@PreAuthorize("hasRole('ADMIN') or hasRole('USER_<SERVICE>')")`.
- Atteignable via le gateway : `GATEWAY_URL/<serviceName><routePrefix>` (ex.
  `/order-service/api/orders`).

« CRUD générique runtime » se traduit donc concrètement par : **une UI unique list+create** servant tous
les resources (forme `{id, name, description}` fixe), pilotée par un catalogue.

## Décisions de design (validées)

### A. Opérations : list + create

Conforme au contrat backend actuel (findAll + create). **Aucune modification du backend.** Edit/Delete/
get-by-id sont explicitement repoussés (nécessiteraient d'étendre le template resource-service — tranche
future).

### B. Catalogue de resources via `@ConfigurationProperties`

`client.resources` dans `application.yml`, lié à un record `@ConfigurationProperties(prefix = "client")`.
Le template embarque le catalogue par défaut (service-a/b/c) ; le générateur réécrit cette liste quand
`resources[]` est fourni. Chaque entrée : `serviceName`, `routePrefix`, `label`, `role`.

### C. Filtrage d'accès par rôle (côté ms-client)

L'UI n'affiche que les resources accessibles : un `ADMIN` voit tout ; un utilisateur ne voit que les
resources dont il possède `ROLE_<role>`. Filtrage dans un helper testable
(`ResourceAccess.accessible(...)`). Le backend reste autoritaire (403 = backstop). Pas de matcher
Spring Security spécifique : `/resources/**` relève de `anyRequest().authenticated()`.

### D. Navigation : index + page par resource

`/resources` (index des resources accessibles) → `/resources/{serviceName}` (table `id/name/description`
+ formulaire d'ajout). URLs claires, une page = une responsabilité.

### E. `GatewayClient` : POST factorisé

`get()` et un nouveau `post(session, path, jsonBody)` partagent un helper privé
`exchangeWithRefresh(session, path, method, body)` portant la logique 401→refresh(rotation)→retry-une-fois
(DRY). Le comportement de `get()` est inchangé (les tests 2b restent verts).

## Architecture & flux

1. `GET /resources` → `ResourceController` filtre le catalogue par rôle via
   `ResourceAccess.accessible(entries, authorities)` → liste de liens (label) vers
   `/resources/{serviceName}`. Vide → « Aucune resource accessible. »
2. `GET /resources/{serviceName}` → si non accessible → `redirect:/resources` ; sinon
   `GatewayClient.get(session, serviceName + routePrefix)` → parse `List<Map<String,Object>>` → table
   `id/name/description` + formulaire d'ajout.
3. `POST /resources/{serviceName}` (`name`, `description`, CSRF) →
   `GatewayClient.post(session, serviceName + routePrefix, {"name","description"})` → **POST-redirect-GET** :
   - succès → `redirect:/resources/{serviceName}` ;
   - erreur backend → `redirect:/resources/{serviceName}?error` ;
   - session expirée → `redirect:/login?expired`.

## Composants (module `ms-client`)

**Nouveaux fichiers (6) :**
- `…/client/config/ClientProperties.java` — `@ConfigurationProperties(prefix = "client")`
  `record ClientProperties(List<ResourceEntry> resources)` ;
  `record ResourceEntry(String serviceName, String routePrefix, String label, String role)`.
- `…/client/security/ResourceAccess.java` — helper statique :
  `accessible(List<ResourceEntry>, Collection<? extends GrantedAuthority>) → List<ResourceEntry>`
  (ADMIN → tout ; sinon entrées dont l'utilisateur a `ROLE_<role>`) et
  `find(entries, authorities, serviceName) → ResourceEntry` (null si non accessible).
- `…/client/web/ResourceController.java` — `@RequestMapping("/resources")` : `GET /`, `GET /{serviceName}`,
  `POST /{serviceName}`.
- `src/main/resources/templates/resources.html` — index.
- `src/main/resources/templates/resource.html` — table + formulaire d'ajout, erreur `${param.error}`.
- `src/test/java/…/client/security/ResourceAccessTest.java` — filtrage par rôle (ADMIN tout ; user son
  resource ; user sans rôle rien).

**Fichiers modifiés (5) :**
- `…/client/service/GatewayClient.java` — refactor `exchangeWithRefresh(...)` partagé + `post(...)`.
- `src/test/java/…/client/service/GatewayClientTest.java` — `+` POST (succès + 401→refresh→retry) ;
  non-régression des tests GET.
- `…/client/ClientApplication.java` — `@EnableConfigurationProperties(ClientProperties.class)`.
- `src/main/resources/application.yml` — `+ client.resources:` (défaut service-a/b/c) **en dernière
  section** du fichier.
- `src/main/resources/templates/home.html` — lien « CRUD » réel `<a th:href="@{/resources}">CRUD</a>`
  (visible par tout authentifié ; remplace « à venir — 2c »).

## Intégration générateur

- **`CrossCuttingConfigProcessor`** : nouvelle réécriture de
  `ms-client/src/main/resources/application.yml` **quand `resources[]` est fourni** — remplace le bloc
  `client:` (placé en dernière section) par la liste construite depuis `resources[]` : `serviceName`,
  `routePrefix` (`routePrefix(r)`), `label` (`r.getClassName()`), `role`
  (`USER_<SERVICE_UPPER>` = `roleName(r)`). Dispatch dans `process()` gardé par `hasResources` +
  `path endsWith "ms-client/src/main/resources/application.yml"`. Le bloc `client:` étant la dernière
  section, le remplacement va de `^client:` à la fin du fichier (robuste, sans surgery d'indentation).
- **`FeatureFilterProcessor`** : nouveaux fichiers sous `ms-client/` → règle existante. Aucune modif.
- **`TemplateLoaderTest`** : parité **133 → 139** (6 nouveaux fichiers).
- **`CrossCuttingConfigProcessorTest`** : `+` test de la réécriture du catalogue (avec `resources[]`,
  l'`application.yml` ms-client contient les serviceNames/routePrefixes/roles attendus et plus
  `service-a`).

## Gestion d'erreurs

- **Resource non accessible** (rôle manquant) → absente de l'index ; accès direct → `redirect:/resources`.
- **Session expirée** → `redirect:/login?expired`.
- **Backend indisponible / réponse illisible** (list) → message « Service indisponible. » sur la page.
- **Échec de création** (backend 4xx/5xx) → `redirect:/resources/{serviceName}?error` ; affichage
  « Échec de la création. » via `${param.error}`.
- **403 backend** (backstop) → traité en message d'erreur, jamais de 500.
- Champs `name`/`description` `required` côté formulaire HTML.

## Tests & vérification

**Tests embarqués (hors-ligne, tournent dans le `mvn test` du projet généré) :**
- `ResourceAccessTest` : ADMIN voit tout ; user voit son resource ; user sans rôle ne voit rien ;
  `find` renvoie null pour un resource non accessible.
- `GatewayClientTest` étendu : POST succès ; POST 401→refresh→retry (tokens de session mis à jour) ;
  les scénarios GET de 2b restent verts (non-régression du refactor `exchangeWithRefresh`).

**Test générateur :** `CrossCuttingConfigProcessorTest` (+1) — réécriture du catalogue ms-client quand
`resources[]` fourni.

**`TemplateLoaderTest`** — parité **139**.

**Vérification end-to-end** (port 8077 ; **rebuilder le jar avant de générer** — piège du jar périmé ;
`pkill` et lancement en commandes séparées ; sandbox désactivé) :
- `clientWebUI=true` avec `resources:[order-service,…]` → les 6 nouveaux fichiers présents ;
  `mvn -pl ms-client -am package` du projet généré **compile ET exécute les tests embarqués verts**
  (sans `-DskipTests`) ; l'`application.yml` généré contient le catalogue `order-service` (et plus
  `service-a`) ; `docker compose config` valide.
- `clientWebUI=false` → `ms-client` absent partout.
- Test runtime complet (login → `/resources` → list/create réels) : **manuel/optionnel** (stack
  complète) — noté NON vérifié automatiquement si l'infra est absente.

## Hors périmètre (noté, non traité ici)

- Edit / Delete / get-by-id (le backend ne les expose pas ; extension du template resource-service =
  tranche future).
- Notifs batch temps réel (2d), chat salon public (2e).
- Pagination / tri / recherche.
- Champs dynamiques : la forme `{id, name, description}` est uniforme et fixe ; le formulaire de création
  est `name` + `description`.

## Fichiers touchés (Phase 2c)

**Template (nouveaux) :** `ms-client/src/main/java/.../config/ClientProperties.java`,
`.../security/ResourceAccess.java`, `.../web/ResourceController.java`,
`ms-client/src/main/resources/templates/resources.html`, `.../templates/resource.html`,
`ms-client/src/test/java/.../security/ResourceAccessTest.java`.

**Template (modifiés) :** `ms-client/src/main/java/.../service/GatewayClient.java`,
`ms-client/src/test/java/.../service/GatewayClientTest.java`,
`ms-client/src/main/java/.../ClientApplication.java`,
`ms-client/src/main/resources/application.yml`, `ms-client/src/main/resources/templates/home.html`.

**Générateur (modifiés) :**
`src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` (réécriture du
catalogue ms-client),
`src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`,
`src/test/java/com/mr486/generator/pipeline/TemplateLoaderTest.java` (parité 133 → 139).
