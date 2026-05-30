# Design : Refactoring PlatformGeneratorService — Pipeline modulaire

**Date :** 2026-05-29  
**Projet :** GestoMS — générateur de patterns microservices Spring Boot  
**Scope :** Remplacer le service monolithique par un pipeline de `FileProcessor` beans Spring injectables

---

## 1. Problème

`PlatformGeneratorService.generate()` est aujourd'hui un copier-coller brut du ZIP template. Il **ignore** tous les champs du `PlatformGenerationRequest` :

| Champ ignoré | Ce qu'il devrait faire |
|---|---|
| `groupId` / `basePackage` | Remplacer les packages dans tout le code généré |
| `javaVersion` | Mettre à jour la version Java dans les pom.xml |
| `features.*` (keycloak, redis, admin…) | Inclure/exclure les composants correspondants |
| `batch.*` (replicas, delay, memory…) | Injecter les valeurs dans docker-compose et .env |
| `resources[]` (ResourceModuleRequest) | Générer les services dynamiquement depuis un template |

Ajouter le support de ces options dans une seule classe produirait un code non-maintenable. Le refactoring prépare une architecture extensible sans modifier `PlatformGeneratorService` pour chaque nouvelle feature.

---

## 2. Architecture cible

```
PlatformGeneratorService (orchestrateur, ~25 lignes)
  │
  ├── ZipTemplateLoader          (charge le ZIP → List<GeneratedFile>)
  ├── GenerationContext          (wraps request + valeurs calculées)
  └── List<FileProcessor>        (injectés par Spring, triés par @Order)
        │
        ├── @Order(10) RootRenameProcessor
        ├── @Order(20) FeatureFilterProcessor
        ├── @Order(30) PackagePlaceholderProcessor
        ├── @Order(40) BatchConfigProcessor
        └── @Order(50) ResourceExpandProcessor
```

**Invariant :** `PlatformGeneratorService` ne change pas quand on ajoute une feature. On crée un nouveau bean `@Component FileProcessor`.

---

## 3. Modèles

### 3.1 GeneratedFile (existant — inchangé)

```java
public record GeneratedFile(String path, byte[] content, boolean executable) {}
```

Utilisé à la fois comme modèle interne du pipeline et comme sortie finale. Pas de classe `RawEntry` supplémentaire.

### 3.2 GenerationContext (nouveau)

```java
@Value  // Lombok immutable
public class GenerationContext {
    PlatformGenerationRequest request;
    String targetRoot;         // request.name normalisé (ex: "my-platform")
    String sourceRoot;         // toujours "ms-platform"
}
```

Calculé une seule fois avant le pipeline. Accessible à chaque processor.

---

## 4. Interface FileProcessor

```java
public interface FileProcessor {
    List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx);
}
```

Contrat : prend une liste, retourne une liste (peut filtrer, transformer ou ajouter des entrées). Chaque processor est **stateless** et **testable isolément**.

---

## 5. PlatformGeneratorService (après refactoring)

```java
@Service
@RequiredArgsConstructor
public class PlatformGeneratorService {

    private final ZipTemplateLoader loader;
    private final List<FileProcessor> processors;  // Spring injecte, triés par @Order

    public List<GeneratedFile> generate(PlatformGenerationRequest request) {
        GenerationContext ctx = new GenerationContext(request, normalize(request.getName()), "ms-platform");
        List<GeneratedFile> files = loader.load();
        for (FileProcessor processor : processors) {
            files = processor.process(files, ctx);
        }
        return files;
    }
}
```

---

## 6. Processors

### 6.1 ZipTemplateLoader

Extrait la logique de chargement ZIP de l'actuel `PlatformGeneratorService`.

```
Responsabilité : lire ms-platform-template.zip → List<GeneratedFile>
Input  : aucun (ressource classpath fixe)
Output : toutes les entrées non-dossier du ZIP
```

### 6.2 RootRenameProcessor — @Order(10)

```
Responsabilité : renommer le dossier racine
Exemple : "ms-platform/service-a/..." → "my-app/service-a/..."
Règle   : remplace le préfixe sourceRoot par targetRoot dans chaque path
```

Migration du comportement actuel de `PlatformGeneratorService`.

### 6.3 FeatureFilterProcessor — @Order(20)

```
Responsabilité : exclure les fichiers des features désactivées
```

Règles d'exclusion par chemin (préfixe après targetRoot) :

| Feature | `false` → exclure ces chemins |
|---|---|
| `keycloak` | `keycloak/` |
| `admin` | `ms-admin/` |
| `grafana` | `observability/grafana/` |
| `loki` | `observability/loki/`, `observability/promtail/` |
| `rabbitmq` | `**/RabbitConfig.java`, `**/BatchNotificationListener.java`, `service-batch/` |
| `redis` | `**/RedisConfig.java`, `**/RedisJobStore.java`, `common-lib/**/RedisKeys.java` |
| `websocket` | `**/WebSocketConfig.java`, `**/batch-notifications.html` |

**Note :** les fichiers `SecurityConfig.java` et `application.yml` ne sont PAS exclus par `keycloak=false` (leur contenu sera géré par `PackagePlaceholderProcessor` via des blocs conditionnels dans une itération future). En V1, on laisse les fichiers de config présents quand `keycloak=false`.

### 6.4 PackagePlaceholderProcessor — @Order(30)

```
Responsabilité : remplacer les valeurs hardcodées par celles du request
```

Transformations sur le **contenu** des fichiers texte (`.java`, `.yml`, `.xml`, `.json`, `.sh`, `.env`, `.md`) :

| Source (template) | Cible (request) |
|---|---|
| `com.mr486.msplatform` | `{request.basePackage}` |
| `com.mr486` (groupId dans pom.xml) | `{request.groupId}` |
| `<java.version>17</java.version>` | `<java.version>{request.javaVersion}</java.version>` |

Transformations sur le **chemin** des fichiers :
- `com/mr486/msplatform/` dans les paths Java → `{basePackage.replace('.','/')}/`
- `com/mr486/` dans les paths Java → `{groupId.replace('.','/')}/`

Condition : s'applique uniquement si `groupId ≠ "com.mr486"` ou `basePackage ≠ "com.mr486.msplatform"` (pas de travail inutile).

Détection binaire : si le contenu n'est pas décodable en UTF-8 (images, ZIP imbriqués), passer le fichier sans transformation.

### 6.5 BatchConfigProcessor — @Order(40)

```
Responsabilité : injecter les valeurs BatchOptions dans les fichiers de configuration
```

Remplacements dans le contenu (fichiers `.yml`, `.env`, `docker-compose.yml`) :

| Placeholder template | Valeur |
|---|---|
| `BATCH_REPLICAS=4` | `BATCH_REPLICAS={batch.replicas}` |
| `BATCH_FILE_CONCURRENCY=5` | `BATCH_FILE_CONCURRENCY={batch.fileConcurrency}` |
| `BATCH_MIN_DELAY_MS=500` | `BATCH_MIN_DELAY_MS={batch.minDelayMs}` |
| `BATCH_MAX_DELAY_MS=1500` | `BATCH_MAX_DELAY_MS={batch.maxDelayMs}` |
| `BATCH_MEMORY_LIMIT=768m` | `BATCH_MEMORY_LIMIT={batch.memoryLimit}` |

Si `batch.enabled = false` : `FeatureFilterProcessor` exclut `service-batch/` via une règle dédiée `batch.enabled` (indépendante de `features.rabbitmq`). `BatchConfigProcessor` vérifie `ctx.getRequest().getBatch().isEnabled()` et retourne la liste inchangée si false.

### 6.6 ResourceExpandProcessor — @Order(50)

```
Responsabilité : générer les services métier dynamiquement depuis resources[]
Stratégie     : service-a comme template source, remplacement de chaînes
```

**Si `resources` est vide** : aucune transformation, les services par défaut (a, b, c) sont conservés.

**Si `resources` est non-vide** : 
1. Supprimer de la liste les entrées correspondant à `service-a/`, `service-b/`, `service-c/`
2. Pour chaque `ResourceModuleRequest` dans `resources[]` :
   - Cloner les fichiers de `service-a/` (déjà renommé en `{targetRoot}/service-a/`)
   - Appliquer les remplacements de chaînes suivants :

| Source (service-a template) | Cible |
|---|---|
| `service-a` (dans path et contenu) | `{serviceName}` |
| `servicea` (package Java) | `{serviceName en minuscules sans tirets — ex: "my-service" → "myservice"}` |
| `ServiceA` (classe Java) | `{className}` |
| `ResourceA` (entité/DTO) | `{className}Resource` ou `{className}` selon le contexte |
| `service_a` (SQL, env) | `{serviceName.replace('-','_')}` |

**Gestion du `databaseType` :**
- `POSTGRESQL` : service-a tel quel (base de référence)
- `H2` : remplacer dans `application.yml` le datasource Postgres par H2 en mémoire + supprimer `db/changelog/` (H2 auto-crée le schéma via Liquibase avec la même config)
- `MONGODB` : remplacer l'entité JPA par un `@Document` Mongo, le `JpaRepository` par un `MongoRepository`, et ajuster `pom.xml` (retirer jpa/postgres, ajouter `spring-boot-starter-data-mongodb`)

**Gestion de l'`idType` :**
- `LONG` : tel quel (type par défaut dans service-a)
- `INTEGER` : remplacer `Long` → `Integer` dans l'entité et repository
- `UUID` : remplacer `Long` → `UUID` + `@GeneratedValue(strategy=AUTO)` → `@GeneratedValue(generator="UUID")`

---

## 7. Structure des packages (après refactoring)

```
com.mr486.generator/
├── controller/
│   └── PlatformGeneratorController.java  (inchangé)
├── dto/                                   (inchangé)
│   ├── PlatformGenerationRequest.java
│   ├── FeatureOptions.java
│   ├── BatchOptions.java
│   ├── ResourceModuleRequest.java
│   ├── DatabaseType.java
│   └── IdType.java
├── model/
│   └── GenerationContext.java             (nouveau)
├── pipeline/
│   ├── FileProcessor.java                 (interface, nouveau)
│   ├── ZipTemplateLoader.java             (extrait de PlatformGeneratorService)
│   └── processor/
│       ├── RootRenameProcessor.java       (nouveau)
│       ├── FeatureFilterProcessor.java    (nouveau)
│       ├── PackagePlaceholderProcessor.java (nouveau)
│       ├── BatchConfigProcessor.java      (nouveau)
│       └── ResourceExpandProcessor.java   (nouveau)
├── service/
│   └── PlatformGeneratorService.java      (réduit à ~25 lignes)
└── zip/
    ├── GeneratedFile.java                 (inchangé)
    └── ZipService.java                    (inchangé)
```

---

## 8. Extensibilité — exemple avec ms-auth (feature Keycloak v2)

Pour intégrer `ms-auth` dans le générateur une fois la feature Keycloak finalisée, il suffira de :

```java
@Component
@Order(60)  // après ResourceExpand
@ConditionalOnProperty(...)  // ou tester ctx.getRequest().getFeatures().isKeycloak()
public class AuthServiceInjectProcessor implements FileProcessor {
    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        if (!ctx.getRequest().getFeatures().isKeycloak()) return files;
        // ajouter les fichiers ms-auth au pipeline
        List<GeneratedFile> result = new ArrayList<>(files);
        result.addAll(loadAuthServiceFiles(ctx));
        return result;
    }
}
```

`PlatformGeneratorService` n'est pas modifié.

---

## 9. Tests unitaires attendus

Chaque processor est testable isolément avec une `List<GeneratedFile>` mockée :

- `FeatureFilterProcessorTest` : vérifier qu'avec `keycloak=false` les fichiers `keycloak/` sont absents
- `PackagePlaceholderProcessorTest` : vérifier le remplacement dans un fichier `.java` et dans un chemin
- `BatchConfigProcessorTest` : vérifier les valeurs dans le `.env` généré
- `ResourceExpandProcessorTest` : vérifier la génération d'un service avec `databaseType=H2` et `idType=UUID`

---

## 10. Hors scope

- Remplacement conditionnel dans `SecurityConfig.java` quand `keycloak=false` (laissé au futur `KeycloakAwareProcessor`)
- Moteur de template (Freemarker/Mustache) — non requis avec l'approche string replacement
- Validation des `ResourceModuleRequest` (unicité des `serviceName`, etc.) — à ajouter dans le controller
- Support de `javaVersion` dans les Dockerfiles (FROM eclipse-temurin:17) — en scope PackagePlaceholderProcessor mais à implémenter en phase 2
