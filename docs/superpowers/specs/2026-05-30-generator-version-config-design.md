# Centralisation des versions du générateur — Design

**Date:** 2026-05-30
**Statut:** spec validé (design approuvé section par section), prêt pour plan d'implémentation
**Périmètre:** extraire toutes les versions aujourd'hui en dur (images Docker + 2 littéraux Maven) vers un fichier de configuration unique **dans le générateur**, et faire en sorte que le projet généré centralise ses images via `.env`.

## Contexte

Les versions sont aujourd'hui dispersées dans le template (`src/main/resources/templates/ms-platform/`) :

- **Images Docker** (`docker-compose.yml`, 9 occurrences statiques) : `postgres:16` (×2), `quay.io/keycloak/keycloak:26.5.6`, `rabbitmq:3.13-management`, `redis:7-alpine`, `mongo:7`, `grafana/loki:3.2.1`, `grafana/promtail:3.2.1`, `grafana/grafana:11.2.2`. **+** `CrossCuttingConfigProcessor` ajoute dynamiquement par resource des blocs `image: postgres:16` / `image: mongo:7` (selon `databaseType`).
- **Base Dockerfile** : `FROM eclipse-temurin:17-jre` dans les **11** `Dockerfile` (un par module buildé).
- **Versions Maven** : les versions sont **déjà** centralisées dans le root `pom.xml` (`<properties>` : `java.version=17`, `spring-cloud.version=2025.0.0`, `mongock.version=5.5.1` ; + parent BOM), sauf **2 littéraux** : le `<parent>` spring-boot `3.5.5` (root pom) et `spring-boot-admin-starter-server` `3.5.5` (`ms-admin/pom.xml`).

Le générateur n'a pas d'`application.yml` aujourd'hui (il tourne sur les défauts Spring Boot). Le pipeline est une chaîne de `FileProcessor` beans `@Order(N)`, transformations **textuelles** sur une `List<GeneratedFile>` :

```
RootRename(10) → FeatureFilter(20) → PackagePlaceholder(30) → BatchConfig(40)
              → ResourceExpand(50) → CrossCuttingConfig(60)
```

`CrossCuttingConfigProcessor` (`@Order(60)`) réécrit le compose **en texte** (regex + StringBuilder) et **ajoute** des blocs `image:` par resource. C'est l'étape qui fixe l'ensemble final des images.

## Décisions de design (validées)

### A. Source de vérité : `application.yml` + `@ConfigurationProperties`

Création de `src/main/resources/application.yml` avec un bloc `platform.versions`, lu par une classe `PlatformVersions` annotée `@ConfigurationProperties("platform.versions")`. Bumper une version = éditer une ligne + régénérer. **Pas de champ ajouté à l'API** `/api/generate/platform` (pas d'override par requête — YAGNI).

### B. Mécanisme : nouveau `VersionInjectionProcessor` `@Order(70)`

Processor dédié, **responsabilité unique « versions »**, tournant **en dernier** (après CrossCutting) pour normaliser uniformément les images statiques **et** celles ajoutées dynamiquement par CrossCutting. Transformations textuelles, cohérentes avec le style des autres processors (garde `containsNullByte`, retour d'une nouvelle liste). On n'étend **pas** CrossCutting (éviter de mélanger « références de services » et « versions »).

### C. Côté projet généré : images pilotées par `.env`, versions Maven en littéral

- **Images Docker → `.env`** : le projet généré centralise toutes ses images dans `.env` (interpolation compose native + `build.args` pour les Dockerfile). Bumpable côté projet livré **sans régénérer**.
- **Versions Maven → littéraux** dans les poms : Maven ne lit pas `.env` ; les versions restent des littéraux, mais **pilotées à la génération** depuis `platform.versions` (root pom + ms-admin).

### D. Template inchangé (littéraux)

Le template garde ses valeurs littérales actuelles ; aucun fichier ajouté/supprimé au template → **`TemplateLoaderTest` parité 173 inchangée**, et `CrossCuttingConfigProcessor` intact.

## Architecture & flux

```
… → CrossCuttingConfig(60)  [détermine services, ajoute blocs resource image: littéraux]
  → VersionInjection(70)    [normalise TOUTES les versions : images→${VAR}, Dockerfile→ARG, .env, poms]
  → ZIP
```

`VersionInjection` voit l'état final (images statiques + dynamiques) et applique des substitutions déterministes. No-op possible si toutes les versions valent les défauts du template (court-circuit comme `PackagePlaceholderProcessor`), mais non requis pour la correction.

## Composants

### `PlatformVersions` (`@ConfigurationProperties("platform.versions")`)

Record/POJO immuable. Bloc `application.yml` :

```yaml
platform:
  versions:
    java-image: eclipse-temurin:17-jre   # base FROM des 11 Dockerfile + build.args JAVA_IMAGE
    java: "17"                            # <java.version> Maven
    spring-boot: 3.5.5                    # <parent> spring-boot + spring-boot-admin (alignés)
    spring-cloud: 2025.0.0
    mongock: 5.5.1
    spring-boot-admin: 3.5.5
    postgres: "16"
    keycloak: 26.5.6                      # tag, repo quay.io/keycloak/keycloak fixe
    rabbitmq: 3.13-management
    redis: 7-alpine
    mongo: "7"
    loki: 3.2.1
    promtail: 3.2.1
    grafana: 11.2.2
```

Les valeurs par défaut du YAML **doivent** égaler les littéraux actuels du template (sinon la génération change de comportement sans intention).

### `VersionInjectionProcessor` (`@Order(70)`, `implements FileProcessor`)

Injecté avec `PlatformVersions`. Pour chaque fichier (garde `containsNullByte`) :

**1. `docker-compose.yml`** — pour chaque image gérée, remplacer `image: <repo>:<tagLittéral>` par `image: <repo>:${<VAR>:-<tagLittéral>}` :
| image | VAR `.env` | forme produite |
|---|---|---|
| `postgres:16` | `POSTGRES_VERSION` | `image: postgres:${POSTGRES_VERSION:-16}` |
| `quay.io/keycloak/keycloak:26.5.6` | `KEYCLOAK_VERSION` | `image: quay.io/keycloak/keycloak:${KEYCLOAK_VERSION:-26.5.6}` |
| `rabbitmq:3.13-management` | `RABBITMQ_VERSION` | `image: rabbitmq:${RABBITMQ_VERSION:-3.13-management}` |
| `redis:7-alpine` | `REDIS_VERSION` | `image: redis:${REDIS_VERSION:-7-alpine}` |
| `mongo:7` | `MONGO_VERSION` | `image: mongo:${MONGO_VERSION:-7}` |
| `grafana/loki:3.2.1` | `LOKI_VERSION` | `image: grafana/loki:${LOKI_VERSION:-3.2.1}` |
| `grafana/promtail:3.2.1` | `PROMTAIL_VERSION` | `image: grafana/promtail:${PROMTAIL_VERSION:-3.2.1}` |
| `grafana/grafana:11.2.2` | `GRAFANA_VERSION` | `image: grafana/grafana:${GRAFANA_VERSION:-11.2.2}` |

Le remplacement étant un `String.replace` du littéral exact (ex. `image: postgres:16`), il s'applique aussi bien aux occurrences statiques qu'aux blocs `postgres`/`mongo` ajoutés par CrossCutting. **Idempotence** : la substitution ne re-matche pas une ligne déjà transformée (le littéral nu n'apparaît plus).

**2. Services applicatifs `build: ./x`** — expansion en forme longue + `build.args` (validé) :
```yaml
  ms-eureka:
    build:
      context: ./ms-eureka
      args:
        JAVA_IMAGE: ${JAVA_IMAGE:-eclipse-temurin:17-jre}
```
Substitution du fragment `build: ./<service>` (forme courte) vers le bloc multi-lignes, en préservant l'indentation. S'applique aux ~11 services buildés (eureka, gateway, admin, client, service-a/b/c, consumer, batch, ms-auth, admin-application + services resource ajoutés par CrossCutting via `build: ./<serviceName>`).

**3. Les 11 `Dockerfile`** — `FROM eclipse-temurin:17-jre` → 
```dockerfile
ARG JAVA_IMAGE=eclipse-temurin:17-jre
FROM ${JAVA_IMAGE}
```
(`ARG` avant `FROM` est la forme Docker correcte ; la valeur par défaut couvre un build direct sans `.env`.)

**4. `dot-env` et `dist.env`** — ajout d'un bloc de versions d'images :
```
# --- image versions ---
JAVA_IMAGE=eclipse-temurin:17-jre
POSTGRES_VERSION=16
KEYCLOAK_VERSION=26.5.6
RABBITMQ_VERSION=3.13-management
REDIS_VERSION=7-alpine
MONGO_VERSION=7
LOKI_VERSION=3.2.1
PROMTAIL_VERSION=3.2.1
GRAFANA_VERSION=11.2.2
```
(`dot-env` est décodé en `.env` à la génération ; `dist.env` est la copie distribuée. Les deux reçoivent le bloc.)

**5. Versions Maven (littéraux pilotés)** :
- root `pom.xml` : `<parent>…<version>3.5.5</version>` → `spring-boot` ; `<java.version>17</java.version>`, `<spring-cloud.version>2025.0.0</spring-cloud.version>`, `<mongock.version>5.5.1</mongock.version>` ← valeurs de `platform.versions`.
- `ms-admin/pom.xml` : `spring-boot-admin-starter-server` `<version>3.5.5</version>` ← `spring-boot-admin`.

Note : `PackagePlaceholderProcessor` (`@Order(30)`) remplace déjà `<java.version>` selon la requête. `VersionInjection` (`@Order(70)`) tourne **après** : pour éviter une double-source, `java.version` du root pom est piloté par la **requête** (`ctx.getRequest().getJavaVersion()`, comportement actuel) et `platform.versions.java` ne sert qu'à documenter/aligner le défaut. Le processor n'écrase `<java.version>` **que s'il vaut encore le littéral `17`** (donc no-op quand la requête l'a déjà changé) — évite tout conflit avec `@Order(30)`.

## Gestion d'erreurs

- Fichier binaire (`containsNullByte`) → ignoré.
- Image présente dans le compose mais absente de `platform.versions` → laissée telle quelle (pas d'échec).
- Interpolation `${VAR:-default}` → `docker compose config` reste valide même **sans** `.env` (défaut intégré).
- `application.yml` absent / clé manquante → binding Spring échoue au démarrage du générateur (fail-fast explicite, pas de génération silencieusement fausse).

## Intégration générateur

- **Nouveaux fichiers générateur** : `src/main/resources/application.yml`, `PlatformVersions.java`, `VersionInjectionProcessor.java` + tests. Ce sont des fichiers **du générateur**, hors `templates/` → **`TemplateLoaderTest` parité 173 inchangée**.
- **Mise à jour du Javadoc** de `FileProcessor` : ajouter l'étape `@Order(70)` à la liste ordonnée.
- `CrossCuttingConfigProcessor` **inchangé**.

## Tests & vérification

- **`PlatformVersionsTest`** : le YAML `platform.versions` se binde correctement (toutes les clés non nulles, défauts = littéraux template).
- **`VersionInjectionProcessorTest`** (unitaire, sans Spring context) :
  - compose : `image: postgres:16` → `${POSTGRES_VERSION:-16}` ; idempotence (2ᵉ passe = no-op) ; image inconnue inchangée.
  - `build: ./ms-eureka` → bloc long-form avec `args: JAVA_IMAGE` et indentation correcte.
  - Dockerfile : `FROM eclipse-temurin:17-jre` → `ARG JAVA_IMAGE=…` + `FROM ${JAVA_IMAGE}`.
  - `.env` (dot-env + dist.env) : bloc versions présent.
  - root pom : `<parent>` spring-boot, `spring-cloud.version`, `mongock.version` pilotés ; ms-admin : `spring-boot-admin` piloté.
  - `<java.version>` : no-op quand la requête a déjà changé la valeur (pas de conflit avec `@Order(30)`).
  - garde binaire.
- **`TemplateLoaderTest`** : parité **173** (inchangée).
- **Vérification end-to-end** (port libre ; **rebuild du jar avant génération** — piège du jar périmé ; `pkill`/lancement en commandes séparées ; sandbox désactivé) :
  - générer (clientWebUI=false + un resource POSTGRES) ; `grep` : compose contient `${POSTGRES_VERSION:-16}` et `args: JAVA_IMAGE`, Dockerfiles contiennent `FROM ${JAVA_IMAGE}`, `.env` contient le bloc versions, root pom + ms-admin pilotés.
  - `mvn -pl ms-eureka -am package` (ou build complet) du projet généré **compile** (les poms restent valides).
  - `docker compose config` **valide** avec ET sans `.env` présent.
  - **Test réel docker** (lancement de la stack) : **manuel/optionnel** par l'utilisateur (« je testerais en réel »).

## Hors périmètre (noté, non traité ici)

- Override des versions par requête API (`versions` dans le payload JSON).
- Centralisation du repo Keycloak (`quay.io/keycloak/keycloak`) — seul le **tag** est variabilisé.
- Versions de plugins Maven (hors `<properties>` déjà gérées par le parent Spring Boot).
- Centralisation côté projet généré des versions Maven dans un BOM maison (les `<properties>` existantes suffisent).

## Fichiers touchés

**Générateur (nouveaux) :** `src/main/resources/application.yml`,
`src/main/java/com/mr486/generator/config/PlatformVersions.java`,
`src/main/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessor.java`,
`src/test/java/.../PlatformVersionsTest.java`,
`src/test/java/.../VersionInjectionProcessorTest.java`.

**Générateur (modifiés) :** `src/main/java/com/mr486/generator/pipeline/FileProcessor.java` (Javadoc ordre).

**Template :** aucun changement (littéraux conservés). `TemplateLoaderTest` parité 173 inchangée.
