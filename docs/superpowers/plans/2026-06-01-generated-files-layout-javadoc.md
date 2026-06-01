# Mise en page + javadoc des fichiers générés — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre la sortie du générateur cohérente en mise en page (4 espaces, ≤ 120 car., 1 import/ligne) et documentée (javadoc française classe + méthodes publiques), sur les **deux sources** (templates statiques + chaînes embarquées dans les processors), verrouillée par un test garde-fou sur les vrais templates.

**Architecture:** Le générateur transforme une `List<GeneratedFile>` via des `FileProcessor` `@Order(N)`. Le contenu Java vient de templates statiques copiés/substitués (`src/main/resources/templates/ms-platform/**`) **et** de chaînes Java embarquées émises à la génération (`ResourceExpandProcessor`, `CrossCuttingConfigProcessor`). Certains remplacements de ces processors sont **couplés à la mise en page exacte** des templates — reformater sans mettre à jour les constantes casse la génération **en silence** (les tests unitaires existants utilisent des fixtures inline, pas les vrais templates). Le garde-fou est donc un test d'intégration qui génère depuis les **vrais** templates.

**Tech Stack:** Java 17, Spring Boot, Maven (`mvn test`), JUnit 5 + AssertJ.

---

## Conventions (référencées par toutes les tâches)

### Règles de mise en page Java
- Indentation **4 espaces**, jamais de tabulation.
- **≤ 120 caractères par ligne** (limite dure, vérifiée par le garde-fou).
- **Un import par ligne** ; jamais `;import ` collé.
- Une annotation de type/méthode par ligne ; les annotations de champ Lombok empilées (`@Getter`) peuvent rester groupées sur la ligne précédant la classe.
- Accolade ouvrante en fin de ligne (style K&R), corps indenté, ligne vide entre membres.
- `=` entouré d'espaces (`strategy = GenerationType.IDENTITY`), espace après les virgules (`<Widget, Long>`).

### Style javadoc (français)
Bloc de **classe/interface** décrivant le rôle, et javadoc sur chaque **méthode publique**. **Exclure** getters/setters Lombok et champs de DTO triviaux.

Exemple classe :
```java
/**
 * Service métier de la ressource {@code ResourceA} : lecture et création via le repository JPA.
 */
```
Exemple méthode publique :
```java
/**
 * Retourne toutes les ressources sous forme de DTO.
 *
 * @return la liste complète des ressources, jamais {@code null}
 */
```
Exemple interface repository :
```java
/**
 * Repository JPA de l'entité {@code ResourceA} (clé primaire {@code Long}).
 */
```

### ⚠️ Tokens à NE JAMAIS modifier (substitution littérale)
Reformater **autour** d'eux, mot pour mot :
- `com.mr486.msplatform`, `com.mr486`, `<java.version>17</java.version>`
- Identifiants clonés par `ResourceExpandProcessor` : `ResourceA`, `ServiceA`, `servicea`, `service-a`, `service_a`, `resources_a`, `resource_a`, `/api/resources-a`, `USER_SERVICE_A`, `SERVICE_A`
- Placeholders Mongo : `{PKG}`, `{CLASS}`, `{COLLECTION}`, `{SERVICE_NAME}`, `{SERVICE_UPPER}`, `{SERVICE_SNAKE}`
- Placeholders `${...}` d'environnement dans les yml ; indentation YAML signifiante.

### ⚠️ poms : style compact conservé (NE PAS expanser)
Les poms utilisent un style **un `<dependency>` par ligne, enfants inline**. `ResourceExpandProcessor` (swap data-jpa→mongo, postgres→h2), `CrossCuttingConfigProcessor` (bloc `<modules>`) et `VersionInjectionProcessor` (versions parent/admin) ciblent ces **chaînes exactes**. **Ne pas** passer les poms en XML multi-ligne. Les `.xml`/pom sont hors périmètre de reformatage (déjà cohérents).

### Couplage processor ↔ format des templates (CRITIQUE)
`ResourceExpandProcessor` contient des remplacements sensibles au format de `service-a/` :
- `applyUuidType` cherche `"@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id"` (une ligne).
- `applyIntegerType`/`applyUuidType` cherchent `"JpaRepository<" + className + ",Long>"` (sans espace après la virgule).

Reformater l'entité/repository `service-a` **oblige** à mettre à jour ces constantes en lockstep (Task 2). Les tests unitaires `ResourceExpandProcessorTest` utilisent des fixtures inline minifiées — ils resteraient verts même si la génération réelle casse. Seul le garde-fou (Task 1) sur vrais templates le détecte.

---

## Task 1 : Garde-fou sur vrais templates (RED)

**Files:**
- Create: `src/test/java/com/mr486/generator/GeneratedOutputLayoutTest.java`

- [ ] **Step 1 : Écrire le test garde-fou (échouera)**

```java
package com.mr486.generator;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.service.PlatformGeneratorService;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GeneratedOutputLayoutTest {

    @Autowired
    PlatformGeneratorService service;

    private ResourceModuleRequest resource(String name, String cls, DatabaseType db, IdType id) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(name);
        r.setClassName(cls);
        r.setDatabaseType(db);
        r.setIdType(id);
        return r;
    }

    private List<GeneratedFile> generateWithResources() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(
            resource("service-widget", "Widget", DatabaseType.POSTGRES, IdType.UUID),
            resource("service-gadget", "Gadget", DatabaseType.MONGO, IdType.LONG)
        ));
        return service.generate(req);
    }

    @Test
    void no_java_line_exceeds_120_chars_and_no_glued_imports() {
        List<GeneratedFile> files = generateWithResources();
        for (GeneratedFile f : files) {
            if (!f.path().endsWith(".java")) continue;
            String content = new String(f.content(), StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                assertThat(lines[i].length())
                    .as("ligne %d de %s dépasse 120 caractères", i + 1, f.path())
                    .isLessThanOrEqualTo(120);
            }
            assertThat(content)
                .as("imports collés dans %s", f.path())
                .doesNotContain(";import ");
        }
    }

    @Test
    void uuid_variant_transform_still_applies_after_reformat() {
        List<GeneratedFile> files = generateWithResources();
        GeneratedFile entity = files.stream()
            .filter(f -> f.path().endsWith("/entity/Widget.java"))
            .findFirst().orElseThrow();
        String content = new String(entity.content(), StandardCharsets.UTF_8);
        assertThat(content).contains("private UUID id");
        assertThat(content).contains("import java.util.UUID");
        assertThat(content).doesNotContain("private Long id");
    }

    @Test
    void mongo_variant_transform_still_applies_after_reformat() {
        List<GeneratedFile> files = generateWithResources();
        GeneratedFile doc = files.stream()
            .filter(f -> f.path().endsWith("/document/Gadget.java"))
            .findFirst().orElseThrow();
        String content = new String(doc.content(), StandardCharsets.UTF_8);
        assertThat(content).contains("@Document");
        assertThat(content).contains("private String id");
    }
}
```

- [ ] **Step 2 : Lancer, confirmer l'échec**

Run: `mvn -q -Dtest=GeneratedOutputLayoutTest test`
Expected: FAIL sur `no_java_line_exceeds_120...` (les templates minifiés actuels ont des lignes de 190 à 931 car.). Les deux autres tests peuvent passer (transforms actuels OK).

- [ ] **Step 3 : Commit**

```bash
git add src/test/java/com/mr486/generator/GeneratedOutputLayoutTest.java
git commit -m "test(generator): layout guard on real templates (≤120, no glued imports, variant transforms)"
```

---

## Task 2 : service-a — reformat + javadoc + constantes processor couplées

C'est la tâche la plus risquée : `service-a/` est le template cloné par `ResourceExpandProcessor`. Reformater **et** mettre à jour les constantes couplées **dans la même tâche**.

**Files:**
- Modify (reformat + javadoc) :
  - `src/main/resources/templates/ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/entity/ResourceA.java`
  - `.../servicea/repository/ResourceARepository.java`
  - `.../servicea/dto/ResourceADto.java`
  - `.../servicea/controller/ResourceAController.java`
  - `.../servicea/service/ResourceAService.java`
- Modify (constantes couplées + Mongo templates) :
  - `src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java`
- Modify (fixtures pour rester représentatives) :
  - `src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java`

- [ ] **Step 1 : Reformater `ResourceA.java`** (entité) — `private Long id;` sur sa propre ligne

```java
package com.mr486.msplatform.servicea.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA de la ressource {@code ResourceA}, mappée sur la table {@code resources_a}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "resources_a")
public class ResourceA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;
}
```
⚠️ Garder `resources_a` et `ResourceA` intacts. Noter que la ligne `@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id` n'existe plus telle quelle → Step 5 met à jour le processor.

- [ ] **Step 2 : Reformater `ResourceARepository.java`**

```java
package com.mr486.msplatform.servicea.repository;

import com.mr486.msplatform.servicea.entity.ResourceA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA de l'entité {@code ResourceA} (clé primaire {@code Long}).
 */
@Repository
public interface ResourceARepository extends JpaRepository<ResourceA, Long> {
}
```
⚠️ Le générique passe de `<ResourceA,Long>` à `<ResourceA, Long>` (espace) → Step 5.

- [ ] **Step 3 : Reformater `ResourceADto.java`**

```java
package com.mr486.msplatform.servicea.dto;

import lombok.*;

/**
 * DTO de transfert de la ressource {@code ResourceA}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceADto {

    private Long id;
    private String name;
    private String description;
}
```

- [ ] **Step 4 : Reformater `ResourceAController.java` et `ResourceAService.java`**

`ResourceAController.java` :
```java
package com.mr486.msplatform.servicea.controller;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.service.ResourceAService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Contrôleur REST de la ressource {@code ResourceA}, exposé sous {@code /api/resources-a}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resources-a")
public class ResourceAController {

    private final ResourceAService service;

    /**
     * Liste toutes les ressources.
     *
     * @return la liste des ressources sous forme de DTO
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public List<ResourceADto> findAll() {
        return service.findAll();
    }

    /**
     * Crée une ressource.
     *
     * @param dto les données de la ressource à créer
     * @return la ressource créée, identifiant renseigné
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public ResourceADto create(@RequestBody ResourceADto dto) {
        return service.create(dto);
    }
}
```

`ResourceAService.java` :
```java
package com.mr486.msplatform.servicea.service;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.entity.ResourceA;
import com.mr486.msplatform.servicea.repository.ResourceARepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service métier de la ressource {@code ResourceA} : lecture et création via le repository JPA.
 */
@Service
@RequiredArgsConstructor
public class ResourceAService {

    private final ResourceARepository repository;

    /**
     * Retourne toutes les ressources sous forme de DTO.
     *
     * @return la liste complète des ressources, jamais {@code null}
     */
    public List<ResourceADto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Persiste une nouvelle ressource à partir de son DTO.
     *
     * @param dto les données à créer
     * @return le DTO de la ressource persistée, identifiant renseigné
     */
    public ResourceADto create(ResourceADto dto) {
        ResourceA entity = ResourceA.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .build();
        return toDto(repository.save(entity));
    }

    private ResourceADto toDto(ResourceA entity) {
        return ResourceADto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .build();
    }
}
```

- [ ] **Step 5 : Mettre à jour les constantes couplées dans `ResourceExpandProcessor.java`**

Dans `applyUuidType` (≈ lignes 327-329), remplacer la recherche de l'entité par le nouveau format multi-ligne :
```java
        text = text.replace(
            "@Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id",
            "@Id\n    @GeneratedValue(strategy = GenerationType.UUID)\n    private UUID id");
```
Dans `applyUuidType` et `applyIntegerType`, le générique repository passe à l'espace :
```java
        text = text.replace("JpaRepository<" + res.getClassName() + ", Long>",
                            "JpaRepository<" + res.getClassName() + ", UUID>");
```
```java
        text = text.replace("JpaRepository<" + res.getClassName() + ", Long>",
                            "JpaRepository<" + res.getClassName() + ", Integer>");
```
(Le remplacement DTO `"private Long id"` → `"private UUID id"`/`"private Integer id"` reste valable, `private Long id;` matche encore.)

- [ ] **Step 6 : Reformater les templates Mongo embarqués dans `ResourceExpandProcessor.java`**

`MONGO_ENTITY_TEMPLATE` (text block lisible + javadoc) :
```java
    private static final String MONGO_ENTITY_TEMPLATE = """
        package {PKG}.document;

        import lombok.*;
        import org.springframework.data.annotation.Id;
        import org.springframework.data.mongodb.core.mapping.Document;

        /**
         * Document MongoDB de la ressource {@code {CLASS}}, stocké dans la collection {@code {COLLECTION}}.
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Document(collection = "{COLLECTION}")
        public class {CLASS} {

            @Id
            private String id;
            private String name;
            private String description;
        }
        """;
```
`MONGO_REPO_TEMPLATE` :
```java
    private static final String MONGO_REPO_TEMPLATE = """
        package {PKG}.repository;

        import {PKG}.document.{CLASS};
        import org.springframework.data.mongodb.repository.MongoRepository;
        import org.springframework.stereotype.Repository;

        /**
         * Repository MongoDB du document {@code {CLASS}} (clé {@code String}).
         */
        @Repository
        public interface {CLASS}Repository extends MongoRepository<{CLASS}, String> {
        }
        """;
```
`MONGO_POM_DEPS` — garder le style compact pom (un `<dependency>` par ligne, indentation 4 espaces comme dans les poms) :
```java
    private static final String MONGO_POM_DEPS =
        "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>\n" +
        "    <dependency><groupId>io.mongock</groupId><artifactId>mongock-springboot-v3</artifactId></dependency>\n" +
        "    <dependency><groupId>io.mongock</groupId><artifactId>mongodb-springdata-v4-driver</artifactId></dependency>";
```
⚠️ Garder tous les `{PKG}`/`{CLASS}`/`{COLLECTION}` intacts. Le `@Document(collection="...")` passe à `collection = "..."` — sans impact (réécrit en entier).

- [ ] **Step 7 : Mettre les fixtures de `ResourceExpandProcessorTest.java` au nouveau format**

Dans `serviceAFiles(...)`, remplacer le contenu inline de l'entité et du repository pour refléter le format reformaté (sinon les fixtures testent un format qui n'existe plus) :
```java
            file(javaPkg + "entity/ResourceA.java",
                "package com.mr486.msplatform.servicea.entity;\n"
              + "import jakarta.persistence.*;\n"
              + "@Entity @Table(name=\"resources_a\")\n"
              + "public class ResourceA {\n\n"
              + "    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id;\n"
              + "    private String name;\n}"),
            file(javaPkg + "repository/ResourceARepository.java",
                "package com.mr486.msplatform.servicea.repository;\n"
              + "import org.springframework.data.jpa.repository.JpaRepository;\n"
              + "public interface ResourceARepository extends JpaRepository<ResourceA, Long> {\n}"),
```

- [ ] **Step 8 : Lancer les tests couplés + garde-fou**

Run: `mvn -q -Dtest=ResourceExpandProcessorTest,GeneratedOutputLayoutTest test`
Expected: `ResourceExpandProcessorTest` PASS (UUID/Mongo/Integer toujours OK), `uuid_variant_...` et `mongo_variant_...` PASS. Le test `no_java_line...` échoue encore (autres modules non traités) — c'est attendu.

- [ ] **Step 9 : Vérifier qu'aucun fichier service-a reformaté ne dépasse 120**

Run:
```bash
find src/main/resources/templates/ms-platform/service-a -name '*.java' \
  -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 10 : Commit**

```bash
git add src/main/resources/templates/ms-platform/service-a src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java
git commit -m "style(template): reformat service-a + javadoc; sync ResourceExpand coupled constants + Mongo templates"
```

---

## Task 3 : service-b & service-c — reformat + javadoc

`service-b` (Mongo `@Document` natif) et `service-c` (JPA) sont conservés en génération par défaut (sans `resources[]`). Mêmes recettes que Task 2, adaptées aux noms (`ResourceB`/`serviceb`/`resources_b`, `ResourceC`/`servicec`/`resources_c`).

**Files (reformat + javadoc) :**
- `service-b/.../document/ResourceB.java`, `dto/ResourceBDto.java`, `repository/ResourceBRepository.java`, `controller/ResourceBController.java`, `service/ResourceBService.java`, `dbchangelogs/DataBaseChangeLog.java`
- `service-c/.../entity/ResourceC.java`, `dto/ResourceCDto.java`, `repository/ResourceCRepository.java`, `controller/ResourceCController.java`, `service/ResourceCService.java`

⚠️ Conserver `ResourceB`/`ResourceC`, `resources_b`/`resources_c`, `@Document(collection="...")` côté service-b. `service-b`/`service-c` ne sont **pas** clonés par ResourceExpand (retirés quand `resources[]` non vide) → pas de constante processor couplée ici.

- [ ] **Step 1 : Reformater chaque fichier** selon les recettes de Task 2 (entité/document, dto, repository, controller, service) + javadoc classe & méthodes publiques. `DataBaseChangeLog.java` (623 car.) : passer la déclaration de classe et les méthodes Mongock sur plusieurs lignes, javadoc de classe.

- [ ] **Step 2 : Vérifier ≤ 120 sur les deux modules**

Run:
```bash
find src/main/resources/templates/ms-platform/service-b src/main/resources/templates/ms-platform/service-c \
  -name '*.java' -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 3 : Garde-fou + suite complète**

Run: `mvn -q test`
Expected: tous les tests existants PASS ; `no_java_line...` toujours rouge (modules restants).

- [ ] **Step 4 : Commit**

```bash
git add src/main/resources/templates/ms-platform/service-b src/main/resources/templates/ms-platform/service-c
git commit -m "style(template): reformat service-b/service-c + javadoc"
```

---

## Task 4 : `*Application.java` + `WebClientConfig` — reformat + javadoc

**Files (reformat + javadoc) :** les mains et configs minifiés :
- `*/…/{ServiceA,ServiceB,ServiceC,Admin,Auth,Consumer,Batch,Eureka,Gateway}Application.java` et `adminapp/AdminAppApplication.java`, `client/ClientApplication.java`
- `service-consumer/.../configuration/WebClientConfig.java` (243 car.)

- [ ] **Step 1 : Reformater chaque main** — imports un par ligne, javadoc de classe. Exemple type :
```java
package com.mr486.msplatform.servicea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Point d'entrée du microservice {@code service-a}.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ServiceAApplication {

    /**
     * Démarre l'application Spring Boot.
     *
     * @param args les arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }
}
```
⚠️ Garder le package `com.mr486.msplatform.*` intact. `WebClientConfig.java` : extraire les imports, indenter le `@Bean`, javadoc classe + méthode `@Bean`.

- [ ] **Step 2 : Vérifier ≤ 120 + suite**

Run:
```bash
find src/main/resources/templates/ms-platform -name '*Application.java' -o -name 'WebClientConfig.java' \
  | xargs awk '{ if (length>120) print FILENAME":"NR": "length }'
mvn -q test
```
Expected: aucune sortie awk ; tests existants PASS.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform
git commit -m "style(template): reformat *Application mains + WebClientConfig + javadoc"
```

---

## Task 5 : AggregateController — template statique + régénération processor

Deux sources à traiter : le **template statique** (`service-consumer`, 931 car.) **et** la méthode `rewriteAggregate` de `CrossCuttingConfigProcessor` (forme `Mono.zip(List.of(...))`).

**Files:**
- Modify: `src/main/resources/templates/ms-platform/service-consumer/src/main/java/com/mr486/msplatform/consumer/controller/AggregateController.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java` (`rewriteAggregate`, ≈ lignes 718-740)

- [ ] **Step 1 : Reformater le template statique**

```java
package com.mr486.msplatform.consumer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrôleur d'agrégation : interroge en parallèle les services métier et fusionne leurs réponses.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AggregateController {

    private final WebClient.Builder webClientBuilder;

    /**
     * Agrège les réponses des services {@code service-a/b/c} en une seule map.
     *
     * @param authorization l'en-tête {@code Authorization} propagé aux services appelés
     * @return une map {nom de service → corps de réponse}
     */
    @GetMapping("/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Map<String, String>> aggregate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return Mono.zip(
                call("lb://service-a/api/resources-a", authorization),
                call("lb://service-b/api/resources-b", authorization),
                call("lb://service-c/api/resources-c", authorization))
            .map(tuple -> {
                Map<String, String> result = new LinkedHashMap<>();
                result.put("service-a", tuple.getT1());
                result.put("service-b", tuple.getT2());
                result.put("service-c", tuple.getT3());
                return result;
            });
    }

    private Mono<String> call(String uri, String authorization) {
        return webClientBuilder.build().get().uri(uri)
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .retrieve()
            .bodyToMono(String.class);
    }
}
```
⚠️ Garder `service-a/api/resources-a` etc. intacts (substitués par ResourceExpand quand resources[] absent reste le défaut ; quand resources[] présent, ce fichier est **remplacé** par `rewriteAggregate` — Step 2).

- [ ] **Step 2 : Reformater la chaîne `body` de `rewriteAggregate`** pour émettre du Java ≤ 120 car. Construire `calls` et `puts` avec sauts de ligne + indentation, et utiliser un text block :

```java
        StringBuilder calls = new StringBuilder();
        StringBuilder puts = new StringBuilder();
        for (int i = 0; i < resources.size(); i++) {
            ResourceModuleRequest r = resources.get(i);
            calls.append("                call(\"lb://").append(r.getServiceName()).append(routePrefix(r))
                 .append("\", authorization)").append(i < resources.size() - 1 ? ",\n" : "\n");
            puts.append("                result.put(\"").append(r.getServiceName())
                .append("\", (String) results[").append(i).append("]);\n");
        }

        String body = ""
            + "package " + pkg + ";\n\n"
            + "import lombok.RequiredArgsConstructor;\n"
            + "import org.springframework.http.HttpHeaders;\n"
            + "import org.springframework.security.access.prepost.PreAuthorize;\n"
            + "import org.springframework.web.bind.annotation.*;\n"
            + "import org.springframework.web.reactive.function.client.WebClient;\n"
            + "import reactor.core.publisher.Mono;\n"
            + "import java.util.LinkedHashMap;\n"
            + "import java.util.Map;\n\n"
            + "/**\n"
            + " * Contrôleur d'agrégation : interroge en parallèle les services métier et fusionne leurs réponses.\n"
            + " */\n"
            + "@RestController\n@RequiredArgsConstructor\n@RequestMapping(\"/api\")\n"
            + "public class AggregateController {\n\n"
            + "    private final WebClient.Builder webClientBuilder;\n\n"
            + "    @GetMapping(\"/aggregate\")\n    @PreAuthorize(\"hasRole('ADMIN')\")\n"
            + "    public Mono<Map<String, String>> aggregate(\n"
            + "            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {\n"
            + "        return Mono.zip(java.util.List.of(\n"
            + calls
            + "            ), results -> {\n"
            + "                Map<String, String> result = new LinkedHashMap<>();\n"
            + puts
            + "                return result;\n"
            + "            });\n"
            + "    }\n\n"
            + "    private Mono<String> call(String uri, String authorization) {\n"
            + "        return webClientBuilder.build().get().uri(uri)\n"
            + "            .header(HttpHeaders.AUTHORIZATION, authorization)\n"
            + "            .retrieve().bodyToMono(String.class);\n"
            + "    }\n"
            + "}\n";
```
⚠️ Conserver la sémantique `Mono.zip(List.of(...), results -> {...})` (signature `Object[] results`, cast `(String) results[i]`). Garder `r.getServiceName()`/`routePrefix(r)` intacts.

- [ ] **Step 3 : Garde-fou + test CrossCutting**

Run: `mvn -q -Dtest=CrossCuttingConfigProcessorTest,GeneratedOutputLayoutTest test`
Expected: `CrossCuttingConfigProcessorTest` PASS ; les assertions de variantes PASS ; `no_java_line...` peut encore échouer si d'autres modules restent.

- [ ] **Step 4 : Commit**

```bash
git add src/main/resources/templates/ms-platform/service-consumer src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java
git commit -m "style: reformat AggregateController (static template + rewriteAggregate output) + javadoc"
```

---

## Task 6 : Sweep javadoc + ≤120 — modules ms-auth & common-lib

Ces fichiers sont **déjà bien indentés** ; il s'agit surtout d'**ajouter la javadoc** (classe + méthodes publiques) et de **wrapper** les rares lignes > 120.

**Files (tous les `.java`) :**
- `ms-auth/.../{AuthApplication, configuration/RestTemplateConfig, configuration/SecurityConfig, controller/AuthController, dto/*, service/AuthService, service/TokenBlacklistService}.java`
- `common-lib/.../{batch/*, constants/RabbitQueues, constants/RedisKeys}.java`

- [ ] **Step 1 : Ajouter la javadoc** classe/interface + méthodes publiques selon le style des Conventions. DTO/records : javadoc de classe seulement (champs ignorés).

- [ ] **Step 2 : Wrapper les lignes > 120** (ex. `AuthService` max 125, `TokenBlacklistService` max 116). Vérifier :
```bash
find src/main/resources/templates/ms-platform/ms-auth src/main/resources/templates/ms-platform/common-lib \
  -name '*.java' -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-auth src/main/resources/templates/ms-platform/common-lib
git commit -m "docs(template): javadoc ms-auth + common-lib; wrap >120 lines"
```

---

## Task 7 : Sweep javadoc + ≤120 — module ms-client (+ tests)

**Files (tous les `.java`, src + test) :** `ms-client/.../{ClientApplication, config/ClientProperties, configuration/*, dto/*, security/*, service/*, web/*}.java` et `src/test/.../{ResourceAccessTest, GatewayClientTest, ChatControllerTest}.java`.

- [ ] **Step 1 : Javadoc** classe + méthodes publiques. Pour les classes de test : javadoc de classe décrivant le sujet testé (les méthodes `@Test` n'ont pas besoin de javadoc, leur nom documente).

- [ ] **Step 2 : Wrapper > 120** (ex. `ResourceController` 119 OK, `ResourceAccessTest` 120 OK ; vérifier après ajout de javadoc qu'aucune ligne ne déborde) :
```bash
find src/main/resources/templates/ms-platform/ms-client -name '*.java' \
  -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform/ms-client
git commit -m "docs(template): javadoc ms-client (+ tests); keep ≤120"
```

---

## Task 8 : Sweep javadoc + ≤120 — admin-application (+ test)

**Files (tous les `.java`) :** `admin-application/.../{AdminAppApplication, configuration/*, dto/*, security/*, service/*, web/*}.java` et `src/test/.../KeycloakAdminClientTest.java` (max 139 → wrapper).

- [ ] **Step 1 : Javadoc** classe + méthodes publiques. `KeycloakAdminClient` (305 lignes) : javadoc sur chaque méthode publique (list/count/update/resetPassword…).

- [ ] **Step 2 : Wrapper > 120** :
```bash
find src/main/resources/templates/ms-platform/admin-application -name '*.java' \
  -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform/admin-application
git commit -m "docs(template): javadoc admin-application (+ test); wrap >120 lines"
```

---

## Task 9 : Sweep javadoc + ≤120 — gateway, eureka, ms-admin, service-batch, service-consumer (restants)

**Files (tous les `.java` non encore traités) :**
- `ms-gateway/.../{GatewayApplication, filter/TokenBlacklistFilter}.java`
- `ms-eureka/.../EurekaApplication.java` (déjà couvert Task 4 si fait — sinon javadoc ici)
- `ms-admin/.../AdminApplication.java`
- `service-batch/.../{BatchApplication, configuration/*, service/BatchWorker, service/RedisJobStore}.java`
- `service-consumer/.../{ConsumerApplication, configuration/*, controller/BatchJobController, messaging/BatchNotificationListener, service/RedisJobStore}.java` (AggregateController traité Task 5, WebClientConfig Task 4)

- [ ] **Step 1 : Javadoc** classe + méthodes publiques sur tous les fichiers listés.

- [ ] **Step 2 : Vérifier ≤ 120** :
```bash
find src/main/resources/templates/ms-platform/ms-gateway src/main/resources/templates/ms-platform/ms-eureka \
     src/main/resources/templates/ms-platform/ms-admin src/main/resources/templates/ms-platform/service-batch \
     src/main/resources/templates/ms-platform/service-consumer -name '*.java' \
  -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform
git commit -m "docs(template): javadoc gateway/eureka/ms-admin/service-batch/service-consumer"
```

---

## Task 10 : Fichiers texte > 120 (html, sql) — passe légère

Périmètre non-Java : seuls quelques fichiers dépassent 120 (mesure : `batch-notifications.html` 150, `service-c 002-seed.sql` 144, `service-a 002-seed.sql` 122). Indentation déjà cohérente ailleurs ; pas de javadoc (non-Java).

**Files:**
- `service-consumer/.../static/batch-notifications.html`
- `service-c/.../db/changelog/002-seed.sql`, `service-a/.../db/changelog/002-seed.sql`

- [ ] **Step 1 : Réduire les lignes > 120** : pour le HTML, couper les attributs/scripts longs ; pour le SQL, passer les `INSERT ... VALUES (...)` multi-lignes (une valeur/ligne) **sans** retirer `ON CONFLICT(...) DO NOTHING` (utilisé par le mode H2 via `replaceAll` — la clause peut rester, le replace est insensible aux espaces autour, mais garder le motif `ON CONFLICT(<cols>) DO NOTHING` sur une seule séquence).

⚠️ `test-all.sh` (122) est **régénéré** par `CrossCuttingConfigProcessor` quand `resources[]` — le template statique n'est pas la source dans ce cas ; le laisser tel quel (script shell, lisibilité > limite stricte). Non bloquant (garde-fou Java-only).

- [ ] **Step 2 : Vérifier** :
```bash
awk '{ if (length>120) print FILENAME":"NR": "length }' \
  src/main/resources/templates/ms-platform/service-consumer/src/main/resources/static/batch-notifications.html \
  src/main/resources/templates/ms-platform/service-a/src/main/resources/db/changelog/002-seed.sql \
  src/main/resources/templates/ms-platform/service-c/src/main/resources/db/changelog/002-seed.sql
```
Expected: aucune sortie.

- [ ] **Step 3 : Commit**

```bash
git add src/main/resources/templates/ms-platform
git commit -m "style(template): wrap >120 lines in html + seed SQL"
```

---

## Task 11 : Acceptation finale

- [ ] **Step 1 : Garde-fou complet vert**

Run: `mvn -q -Dtest=GeneratedOutputLayoutTest test`
Expected: les 3 tests PASS (≤120 + pas d'imports collés + variantes UUID/Mongo).

- [ ] **Step 2 : Suite complète**

Run: `mvn -q test`
Expected: BUILD SUCCESS, tous les tests verts.

- [ ] **Step 3 : Sanity compile d'un module généré** (la sortie n'est pas compilée par le build). Générer une plateforme par défaut et compiler un service pour valider que les templates reformatés restent du Java valide :
```bash
# via un test jetable ou l'endpoint /api/generate/platform, dézipper puis :
#   cd <output>/service-a && mvn -q -o compile   (ou un module au choix)
```
Expected: compilation OK (pas d'erreur de syntaxe introduite par le reformatage). Si l'environnement hors-ligne empêche le build Maven du projet généré, valider au moins visuellement l'équilibre des accolades sur 2-3 fichiers reformatés.

- [ ] **Step 4 : Vérification globale ≤120 sur tout le template**

Run:
```bash
find src/main/resources/templates/ms-platform -name '*.java' \
  -exec awk '{ if (length>120) print FILENAME":"NR": "length }' {} +
```
Expected: aucune sortie.

- [ ] **Step 5 : Commit final éventuel** (si ajustements)

```bash
git add -A && git commit -m "chore: finalize generated-files layout + javadoc"
```

---

## Self-review (couverture spec)

- **Objectif 1 (mise en page Java 4 esp./120)** → Tasks 2-5 (reformat) + 6-9 (wrap résiduel) + garde-fou Task 1/11.
- **Objectif 1 (autres fichiers texte)** → Task 10 (html/sql) ; poms volontairement conservés compacts (justifié, couplage processors) ; yml déjà ≤113 conformes.
- **Objectif 2 (javadoc FR classe + méthodes publiques, tous les Java)** → Tasks 2-9.
- **Objectif 3 (garde-fou)** → Task 1, vérifié Task 11 ; couvre les deux sources (génère avec `resources[]`).
- **Source A (templates)** → Tasks 2,3,4,5(statique),6,7,8,9,10.
- **Source B (chaînes processors)** → Task 2 (Mongo entity/repo/pom), Task 5 (rewriteAggregate). `test-all.sh`/compose/realm : mise en page shell/yaml/json non bloquante (garde-fou Java-only) — laissés tels quels, documenté Task 10.
- **Pièges** → tokens/poms/couplage documentés en tête ; constantes ResourceExpand mises à jour Task 2 ; fixtures synchronisées Task 2.
