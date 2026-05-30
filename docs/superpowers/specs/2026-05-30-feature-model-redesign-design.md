# Phase 1 — Refonte du modèle de features

**Date:** 2026-05-30
**Statut:** spec validé section par section, prêt pour plan d'implémentation
**Périmètre:** fondation uniquement. Aucune nouvelle application générée (ms-client → Phase 2, admin-application → Phase 3).

## Contexte

Le générateur GestoMS produit une plateforme microservices en transformant un template décompressé
(`src/main/resources/templates/ms-platform/`, 112 fichiers) via une chaîne de processors ordonnés.
Le modèle de features actuel expose 7 bascules indépendantes
(`keycloak, redis, rabbitmq, websocket, admin, grafana, loki`). On veut le simplifier pour refléter
une nouvelle réalité produit :

- **Toujours installés** (plus aucun toggle) : keycloak (+ ms-auth), redis, rabbitmq, websocket.
- **Toujours installé** : `admin-application` (nouveau module CRUD users/roles — *créé en Phase 3*).
- **Optionnels** : `springboot-admin` (= l'actuel module `ms-admin`, monitoring Spring Boot Admin) et
  `client` (= nouveau module `ms-client`, UI Thymeleaf — *créé en Phase 2*).
- **Observabilité** (loki + promtail + grafana) : installée si `batch.grafana = true`.

Cette Phase 1 ne câble QUE ce qui a un support réel dans le template aujourd'hui. Les références aux
modules `ms-client` et `admin-application` (routes gateway, modules pom, blocs compose) seront ajoutées
dans leur phase respective, en même temps que le module template correspondant, pour ne jamais créer de
référence orpheline (un module/route pointant vers un dossier absent casse `mvn package` /
`docker compose up`).

## Découpage global (rappel)

- **Phase 1 (ce spec)** : refonte du modèle de features. Fondation.
- **Phase 2** : module `ms-client` (UI Thymeleaf+JS, auth BFF via ms-auth, CRUD générique runtime,
  page consumer, chat salon public, notifs batch temps réel). Activé par `features.client`.
- **Phase 3** : module `admin-application` (UI Thymeleaf+JS réservée `ROLE_ADMIN`, CRUD roles/users via
  la Keycloak Admin REST API). Toujours installé.

Chaque phase a son propre spec → plan → implémentation.

## Décisions de design (validées)

### A. Modèle de DTO

**`FeatureOptions`** — réduit à deux booléens :

```java
@Data
public class FeatureOptions {
    /** Module ms-admin (monitoring Spring Boot Admin). Optionnel. */
    @JsonProperty("springboot-admin")
    private boolean springbootAdmin = false;
    /** Module ms-client (UI Thymeleaf). Optionnel. [le module arrive en Phase 2] */
    private boolean client = false;
}
```

- Les champs `keycloak/redis/rabbitmq/websocket/admin/grafana/loki` sont **supprimés**.
- `@JsonProperty("springboot-admin")` mappe exactement la clé kebab-case de la commande utilisateur.
- Jackson est configuré pour **ignorer les propriétés inconnues** (`FAIL_ON_UNKNOWN_PROPERTIES=false`,
  comportement Spring Boot par défaut) : une ancienne commande contenant les anciens flags ne plante pas,
  elle perd simplement ces toggles. À vérifier explicitement par un test.

**`BatchOptions`** — gagne `grafana`, conserve le reste :

```java
private boolean enabled = true;
private boolean grafana = false;   // true → installe loki + promtail + grafana (observability complète)
private int replicas = 4;
private int fileConcurrency = 5;
private long minDelayMs = 500;
private long maxDelayMs = 1500;
private String memoryLimit = "768m";
```

`batch.grafana` pilote **toute** la stack `observability/` (loki + promtail + grafana), pas seulement
grafana — grafana sans loki/promtail n'aurait rien à afficher.

### B. `FeatureFilterProcessor` (@Order(20), filtrage des chemins)

Passe de 8 règles à 3 (+ batch) :

```java
private boolean include(String path, String root, FeatureOptions f, BatchOptions b) {
    String rel = relative(path, root);
    if (!f.isSpringbootAdmin() && rel.startsWith("ms-admin/"))      return false;
    if (!f.isClient()          && rel.startsWith("ms-client/"))     return false; // module absent en P1
    if (!b.isGrafana()         && rel.startsWith("observability/")) return false;
    if (!b.isEnabled()         && rel.startsWith("service-batch/")) return false;
    return true;
}
```

Disparaissent : toutes les règles keycloak/ms-auth, redis (`RedisConfig`/`RedisJobStore`/`RedisKeys`),
rabbitmq (`RabbitConfig`/`BatchNotificationListener`), websocket
(`WebSocketConfig`/`batch-notifications.html`). Ces fichiers sont désormais toujours conservés.

La règle `observability/` unique remplace les deux anciennes règles séparées loki/grafana.

La règle `ms-client/` est posée dès maintenant (inerte tant que le dossier n'existe pas) ; elle
deviendra active quand le module sera ajouté en Phase 2.

### C. `CrossCuttingConfigProcessor` (@Order(60), références transverses)

Quatre méthodes réalignées. En Phase 1, **aucune référence à `ms-client` ni `admin-application`**
(Option 1 — voir section D).

**1. `desiredModules()`** :
```
common-lib, ms-eureka, ms-gateway,
ms-auth,                                 (toujours — keycloak permanent)
service-consumer,
[service-a, service-b, service-c]         si resources[] vide,
service-batch                             si batch.enabled,
ms-admin                                  si features.springbootAdmin,
[<service> par resource]
```
Retiré : `if (keycloak) ms-auth` → `ms-auth` devient inconditionnel.
*Non ajouté en Phase 1* : `admin-application` (Phase 3), `ms-client` (Phase 2).

**2. `blocksToRemove()`** :
```
si !batch.enabled              → service-batch
si !batch.grafana             → loki, promtail, grafana
si !features.springbootAdmin → ms-admin
si resources[] présent        → service-a-db, service-b-db, service-a, service-b, service-c
```
Retiré : bloc keycloak/keycloak-db/ms-auth, rabbitmq, redis (jamais retirés désormais).
*Non ajouté en Phase 1* : retrait conditionnel de `ms-client` (Phase 2).

**3. `volumesToRemove()`** :
```
si resources[] présent        → service_a_db_data, service_b_db_data
```
Retiré : `keycloak_db_data`, `redis_data` (keycloak/redis permanents).

**4. `rewriteGatewayYml()`** :
- La route `ms-auth` n'est plus jamais retirée.
- Inchangé par ailleurs : retrait des routes service-a/b/c + ajout d'une route par resource.
- *Non ajouté en Phase 1* : routes `admin-application`/`ms-client` (ajoutées en Phase 3/2 avec leur module).

### D. Gestion du décalage de phasage (Option 1)

Les modules `ms-client` et `admin-application` n'existent pas encore dans le template en Phase 1.
Pour éviter toute référence orpheline, la Phase 1 ne câble dans `CrossCuttingConfigProcessor` que ce qui
a un support réel dans le template du jour. La logique `springbootAdmin`/`client` vit dès la Phase 1 dans
les DTOs et le filtre (un `client=true` ne fait simplement rien tant que le module n'existe pas), mais le
processor transverse ne référence `ms-client`/`admin-application` qu'à partir de leur phase. Chaque phase
reste verte (`mvn package` du projet généré OK).

### E. Tests & vérification

**Tests unitaires :**

- `FeatureFilterProcessorTest` — réécrit sur le nouveau modèle :
  - `springbootAdmin=false` retire `ms-admin/` ; `=true` le garde.
  - `batch.grafana=false` retire tout `observability/` ; `=true` le garde.
  - `batch.enabled=false` retire `service-batch/`.
  - keycloak/ms-auth/redis/rabbitmq/websocket **toujours présents** quel que soit l'input
    (tests de non-régression positifs).
  - `client` : neutre en Phase 1 (module absent).
- `CrossCuttingConfigProcessorTest` — `desiredModules` inclut toujours `ms-auth` ; assertions
  « keycloak/redis retirés » remplacées par « jamais retirés » ; route `ms-auth` toujours présente.
  `admin-application` **pas** attendu dans les modules en Phase 1 (Option 1).
- `TemplateLoaderTest` — compteur de parité **inchangé (112)** : aucun fichier template ajouté/retiré
  en Phase 1.
- Test Jackson : un JSON contenant les anciens flags (`keycloak`, `loki`, …) + `springboot-admin`
  désérialise sans erreur et mappe correctement `springbootAdmin`.

**Vérification end-to-end :** générer avec la commande de référence (ci-dessous), puis :
- `mvn package` du projet généré passe (aucun module/route orphelin) ;
- `docker compose config` valide le `docker-compose.yml` ;
- observability présente (grafana=true), service-batch présent, ms-admin présent ;
- keycloak/ms-auth/redis/rabbitmq toujours présents ;
- `client=true` n'a encore aucun effet (normal en Phase 1).

Vérification faite sur le **zip réellement servi par l'endpoint** (pas seulement la sortie statique), en
prenant garde au piège connu : un serveur `java -jar` zombie sur le port 8080 sert un jar périmé — lancer
sur un port dédié ou tuer les serveurs avant. Check sans serveur : `unzip -p` dans le jar fraîchement
buildé.

## Commande de référence (JSON corrigé)

La commande fournie par l'utilisateur contenait deux erreurs JSON (virgule finale après `"grafana": true`
et virgule manquante entre `"springboot-admin"` et `"client"`). Version valide :

```bash
curl -X POST http://localhost:8080/api/generate/platform \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ms-platform",
    "groupId": "com.acme",
    "basePackage": "com.acme.shop",
    "javaVersion": "17",
    "resources": [
      { "serviceName": "order-service",     "className": "Order",   "routePrefix": "/api/orders",   "databaseType": "POSTGRES", "idType": "LONG" },
      { "serviceName": "product-service",   "className": "Product", "routePrefix": "/api/products", "databaseType": "MONGO" },
      { "serviceName": "inventory-service", "className": "Item",    "routePrefix": "/api/items",    "databaseType": "H2", "idType": "UUID" }
    ],
    "batch": {
      "enabled": true, "replicas": 4, "fileConcurrency": 5,
      "minDelayMs": 500, "maxDelayMs": 1500, "memoryLimit": "768m",
      "grafana": true
    },
    "features": {
      "springboot-admin": true,
      "client": true
    }
  }' \
  --output ms-platform.zip
```

## Hors périmètre (noté, non traité ici)

- `BatchConfigProcessor` ne gère **pas** `batch.grafana` : il n'injecte que replicas / fileConcurrency /
  délais / memoryLimit dans les placeholders `BATCH_*`. Le pilotage de l'observabilité par `batch.grafana`
  se fait entièrement par présence/absence de fichiers (`FeatureFilterProcessor`) et retrait de blocs
  compose (`CrossCuttingConfigProcessor`) — aucune variable d'environnement ne conditionne grafana. Donc
  `BatchConfigProcessor` n'a **rien à changer** en Phase 1 (le nouveau champ `grafana` y est simplement
  ignoré). À confirmer : `isDefault()` n'a pas besoin de tester `grafana`.
- Création effective des modules `ms-client` (Phase 2) et `admin-application` (Phase 3).

## Fichiers touchés (Phase 1)

- `src/main/java/com/mr486/generator/dto/FeatureOptions.java` — réécrit.
- `src/main/java/com/mr486/generator/dto/BatchOptions.java` — `+ grafana`.
- `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` — règles réécrites.
- `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` —
  `desiredModules` / `blocksToRemove` / `volumesToRemove` / `rewriteGatewayYml`.
- `src/test/java/.../FeatureFilterProcessorTest.java` — réécrit.
- `src/test/java/.../CrossCuttingConfigProcessorTest.java` — mis à jour.
- (éventuel) test Jackson de désérialisation tolérante.

Aucun fichier sous `templates/ms-platform/` n'est ajouté ou retiré en Phase 1.
