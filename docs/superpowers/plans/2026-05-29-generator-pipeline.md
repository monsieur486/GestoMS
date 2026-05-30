# Generator Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactorer `PlatformGeneratorService` en un pipeline de `FileProcessor` beans Spring `@Order`-triés, implémentant tous les champs de `PlatformGenerationRequest` (features, packages, batch, resources dynamiques).

**Architecture:** Le service orchestre une `List<FileProcessor>` injectée et triée par Spring. Chaque processor est un bean `@Component` stateless avec une responsabilité unique. Ajouter une feature = créer un nouveau bean, sans modifier le service.

**Tech Stack:** Spring Boot 3.5.5, Java 17, JUnit 5 (Jupiter), AssertJ, Maven

---

## Fichiers

### Créer
| Fichier | Rôle |
|---|---|
| `src/main/java/com/mr486/generator/model/GenerationContext.java` | Contexte immuable du pipeline |
| `src/main/java/com/mr486/generator/pipeline/FileProcessor.java` | Interface du pipeline |
| `src/main/java/com/mr486/generator/pipeline/ZipTemplateLoader.java` | Chargement du ZIP template |
| `src/main/java/com/mr486/generator/pipeline/processor/RootRenameProcessor.java` | `@Order(10)` rename racine |
| `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java` | `@Order(20)` filtre features |
| `src/main/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessor.java` | `@Order(30)` remplacement packages |
| `src/main/java/com/mr486/generator/pipeline/processor/BatchConfigProcessor.java` | `@Order(40)` config batch |
| `src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java` | `@Order(50)` services dynamiques |
| `src/test/java/com/mr486/generator/pipeline/processor/ProcessorTestHelper.java` | Helpers partagés par les tests |
| `src/test/java/com/mr486/generator/pipeline/processor/RootRenameProcessorTest.java` | |
| `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java` | |
| `src/test/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessorTest.java` | |
| `src/test/java/com/mr486/generator/pipeline/processor/BatchConfigProcessorTest.java` | |
| `src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java` | |
| `src/test/java/com/mr486/generator/service/PlatformGeneratorServiceIntegrationTest.java` | |

### Modifier
| Fichier | Modification |
|---|---|
| `pom.xml` | Ajouter `spring-boot-starter-test` |
| `src/main/java/com/mr486/generator/service/PlatformGeneratorService.java` | Réduire à ~25 lignes (orchestrateur) |

---

## Task 1 — Infrastructure : GenerationContext + FileProcessor + test dependency

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/mr486/generator/model/GenerationContext.java`
- Create: `src/main/java/com/mr486/generator/pipeline/FileProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/ProcessorTestHelper.java`
- Create: `src/test/java/com/mr486/generator/model/GenerationContextTest.java`

- [ ] **Ajouter spring-boot-starter-test dans pom.xml**

Dans `pom.xml`, ajouter dans `<dependencies>` :
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Créer GenerationContext**

```java
// src/main/java/com/mr486/generator/model/GenerationContext.java
package com.mr486.generator.model;

import com.mr486.generator.dto.PlatformGenerationRequest;
import lombok.Value;

@Value
public class GenerationContext {
    PlatformGenerationRequest request;
    String targetRoot;
    String sourceRoot;

    public static GenerationContext from(PlatformGenerationRequest request) {
        String name = request.getName();
        String target = (name == null || name.isBlank()) ? "ms-platform" : name.trim();
        return new GenerationContext(request, target, "ms-platform");
    }
}
```

- [ ] **Créer FileProcessor**

```java
// src/main/java/com/mr486/generator/pipeline/FileProcessor.java
package com.mr486.generator.pipeline;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;

public interface FileProcessor {
    List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx);
}
```

- [ ] **Créer ProcessorTestHelper**

```java
// src/test/java/com/mr486/generator/pipeline/processor/ProcessorTestHelper.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProcessorTestHelper {

    public static GeneratedFile file(String path, String content) {
        return new GeneratedFile(path, content.getBytes(StandardCharsets.UTF_8), false);
    }

    public static GeneratedFile file(String path, String content, boolean executable) {
        return new GeneratedFile(path, content.getBytes(StandardCharsets.UTF_8), executable);
    }

    public static String contentOf(GeneratedFile f) {
        return new String(f.content(), StandardCharsets.UTF_8);
    }

    public static GenerationContext defaultCtx() {
        return GenerationContext.from(new PlatformGenerationRequest());
    }

    public static GenerationContext ctxWithName(String name) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName(name);
        return GenerationContext.from(req);
    }

    public static GenerationContext ctxWithPackage(String groupId, String basePackage) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setGroupId(groupId);
        req.setBasePackage(basePackage);
        return GenerationContext.from(req);
    }

    public static GenerationContext ctxWithFeatures(FeatureOptions features) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setFeatures(features);
        return GenerationContext.from(req);
    }
}
```

- [ ] **Écrire le test GenerationContext**

```java
// src/test/java/com/mr486/generator/model/GenerationContextTest.java
package com.mr486.generator.model;

import com.mr486.generator.dto.PlatformGenerationRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GenerationContextTest {

    @Test
    void uses_request_name_as_target_root() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-platform");
        GenerationContext ctx = GenerationContext.from(req);
        assertThat(ctx.getTargetRoot()).isEqualTo("my-platform");
        assertThat(ctx.getSourceRoot()).isEqualTo("ms-platform");
    }

    @Test
    void falls_back_to_ms_platform_when_name_blank() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("  ");
        GenerationContext ctx = GenerationContext.from(req);
        assertThat(ctx.getTargetRoot()).isEqualTo("ms-platform");
    }

    @Test
    void falls_back_to_ms_platform_when_name_null() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName(null);
        GenerationContext ctx = GenerationContext.from(req);
        assertThat(ctx.getTargetRoot()).isEqualTo("ms-platform");
    }
}
```

- [ ] **Lancer les tests**

```bash
mvn test -Dtest=GenerationContextTest
```
Attendu : `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Commit**

```bash
git add pom.xml src/main/java/com/mr486/generator/model/ src/main/java/com/mr486/generator/pipeline/FileProcessor.java src/test/
git commit -m "feat: add pipeline infrastructure (GenerationContext, FileProcessor)"
```

---

## Task 2 — ZipTemplateLoader

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/ZipTemplateLoader.java`
- Create: `src/test/java/com/mr486/generator/pipeline/ZipTemplateLoaderTest.java`

- [ ] **Écrire le test**

```java
// src/test/java/com/mr486/generator/pipeline/ZipTemplateLoaderTest.java
package com.mr486.generator.pipeline;

import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ZipTemplateLoaderTest {

    private final ZipTemplateLoader loader = new ZipTemplateLoader();

    @Test
    void loads_non_empty_file_list() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).isNotEmpty();
    }

    @Test
    void all_entries_have_ms_platform_prefix() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).allMatch(f -> f.path().startsWith("ms-platform/"));
    }

    @Test
    void no_directory_entries() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).allMatch(f -> !f.path().endsWith("/"));
    }

    @Test
    void marks_sh_files_as_executable() {
        List<GeneratedFile> files = loader.load();
        assertThat(files).anyMatch(f -> f.path().endsWith(".sh") && f.executable());
    }
}
```

- [ ] **Lancer le test (doit échouer)**

```bash
mvn test -Dtest=ZipTemplateLoaderTest
```
Attendu : `Cannot find symbol: ZipTemplateLoader`

- [ ] **Créer ZipTemplateLoader**

```java
// src/main/java/com/mr486/generator/pipeline/ZipTemplateLoader.java
package com.mr486.generator.pipeline;

import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ZipTemplateLoader {

    private static final String TEMPLATE = "templates/ms-platform-template.zip";

    public List<GeneratedFile> load() {
        List<GeneratedFile> files = new ArrayList<>();
        try (InputStream is = new ClassPathResource(TEMPLATE).getInputStream();
             ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String path = entry.getName();
                byte[] content = readAll(zip);
                boolean executable = path.endsWith(".sh") || path.endsWith("mvnw");
                files.add(new GeneratedFile(path, content, executable));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load platform template", ex);
        }
        return files;
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
```

- [ ] **Lancer les tests (doivent passer)**

```bash
mvn test -Dtest=ZipTemplateLoaderTest
```
Attendu : `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/ZipTemplateLoader.java src/test/java/com/mr486/generator/pipeline/ZipTemplateLoaderTest.java
git commit -m "feat: add ZipTemplateLoader"
```

---

## Task 3 — RootRenameProcessor + refactoring PlatformGeneratorService

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/processor/RootRenameProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/RootRenameProcessorTest.java`
- Modify: `src/main/java/com/mr486/generator/service/PlatformGeneratorService.java`
- Create: `src/test/java/com/mr486/generator/service/PlatformGeneratorServiceIntegrationTest.java`

- [ ] **Écrire le test RootRenameProcessor**

```java
// src/test/java/com/mr486/generator/pipeline/processor/RootRenameProcessorTest.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class RootRenameProcessorTest {

    private final RootRenameProcessor processor = new RootRenameProcessor();

    @Test
    void renames_source_root_to_target_root() {
        GenerationContext ctx = ctxWithName("my-app");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/pom.xml", "<project/>"),
            file("ms-platform/docker-compose.yml", "version: '3'")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result).extracting(GeneratedFile::path)
            .containsExactly("my-app/service-a/pom.xml", "my-app/docker-compose.yml");
    }

    @Test
    void keeps_content_and_executable_flag_unchanged() {
        GenerationContext ctx = ctxWithName("x");
        List<GeneratedFile> input = List.of(file("ms-platform/go.sh", "#!/bin/bash", true));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).executable()).isTrue();
        assertThat(contentOf(result.get(0))).isEqualTo("#!/bin/bash");
    }

    @Test
    void is_noop_when_name_is_default() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = List.of(file("ms-platform/pom.xml", "content"));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).path()).isEqualTo("ms-platform/pom.xml");
    }
}
```

- [ ] **Lancer le test (doit échouer)**

```bash
mvn test -Dtest=RootRenameProcessorTest
```

- [ ] **Créer RootRenameProcessor**

```java
// src/main/java/com/mr486/generator/pipeline/processor/RootRenameProcessor.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Order(10)
public class RootRenameProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        if (ctx.getSourceRoot().equals(ctx.getTargetRoot())) return files;
        String prefix = ctx.getSourceRoot() + "/";
        return files.stream()
            .map(f -> f.path().startsWith(prefix)
                ? new GeneratedFile(ctx.getTargetRoot() + f.path().substring(prefix.length() - 1), f.content(), f.executable())
                : f)
            .toList();
    }
}
```

- [ ] **Lancer le test (doit passer)**

```bash
mvn test -Dtest=RootRenameProcessorTest
```
Attendu : `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Refactoriser PlatformGeneratorService**

Remplacer le contenu complet de `src/main/java/com/mr486/generator/service/PlatformGeneratorService.java` :

```java
// src/main/java/com/mr486/generator/service/PlatformGeneratorService.java
package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.pipeline.ZipTemplateLoader;
import com.mr486.generator.zip.GeneratedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformGeneratorService {

    private final ZipTemplateLoader loader;
    // Spring injecte et trie par @Order automatiquement
    private final List<FileProcessor> processors;

    public List<GeneratedFile> generate(PlatformGenerationRequest request) {
        GenerationContext ctx = GenerationContext.from(request);
        List<GeneratedFile> files = loader.load();
        for (FileProcessor processor : processors) {
            files = processor.process(files, ctx);
        }
        return files;
    }
}
```

- [ ] **Écrire le test d'intégration**

```java
// src/test/java/com/mr486/generator/service/PlatformGeneratorServiceIntegrationTest.java
package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlatformGeneratorServiceIntegrationTest {

    @Autowired
    PlatformGeneratorService service;

    @Test
    void generates_non_empty_output() {
        List<GeneratedFile> files = service.generate(new PlatformGenerationRequest());
        assertThat(files).isNotEmpty();
    }

    @Test
    void renames_root_when_name_provided() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-project");
        List<GeneratedFile> files = service.generate(req);
        assertThat(files).allMatch(f -> f.path().startsWith("my-project/"));
        assertThat(files).noneMatch(f -> f.path().startsWith("ms-platform/"));
    }
}
```

- [ ] **Lancer tous les tests**

```bash
mvn test
```
Attendu : tous les tests passent, incluant `PlatformGeneratorServiceIntegrationTest`

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/ src/test/java/com/mr486/generator/
git commit -m "feat: refactor PlatformGeneratorService to modular pipeline"
```

---

## Task 4 — FeatureFilterProcessor

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`

- [ ] **Écrire les tests**

```java
// src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class FeatureFilterProcessorTest {

    private final FeatureFilterProcessor processor = new FeatureFilterProcessor();

    private List<GeneratedFile> sampleFiles() {
        return List.of(
            file("ms-platform/keycloak/import/realm.json", "{}"),
            file("ms-platform/ms-admin/pom.xml", "<project/>"),
            file("ms-platform/observability/grafana/dashboards/d.json", "{}"),
            file("ms-platform/observability/promtail/config.yml", ""),
            file("ms-platform/service-batch/pom.xml", "<project/>"),
            file("ms-platform/service-consumer/src/main/java/x/RabbitConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/RedisConfig.java", "class R{}"),
            file("ms-platform/service-consumer/src/main/java/x/WebSocketConfig.java", "class W{}"),
            file("ms-platform/service-consumer/src/main/resources/static/batch-notifications.html", "<html/>"),
            file("ms-platform/docker-compose.yml", "services:")
        );
    }

    @Test
    void keeps_all_files_when_all_features_enabled() {
        FeatureOptions all = new FeatureOptions();
        // defaults: all enabled except grafana/loki
        all.setGrafana(true);
        all.setLoki(true);
        GenerationContext ctx = ctxWithFeatures(all);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        assertThat(result).hasSize(sampleFiles().size());
    }

    @Test
    void excludes_keycloak_dir_when_keycloak_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setKeycloak(false);
        GenerationContext ctx = ctxWithFeatures(f);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctx);
        assertThat(result).noneMatch(e -> e.path().contains("/keycloak/"));
    }

    @Test
    void excludes_ms_admin_when_admin_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setAdmin(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().contains("/ms-admin/"));
    }

    @Test
    void excludes_grafana_dir_when_grafana_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setGrafana(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().contains("/grafana/"));
    }

    @Test
    void excludes_promtail_dir_when_loki_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setLoki(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().contains("/promtail/"));
    }

    @Test
    void excludes_service_batch_when_batch_disabled() {
        FeatureOptions f = new FeatureOptions();
        BatchOptions batch = new BatchOptions();
        batch.setEnabled(false);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setFeatures(f);
        req.setBatch(batch);
        List<GeneratedFile> result = processor.process(sampleFiles(), GenerationContext.from(req));
        assertThat(result).noneMatch(e -> e.path().contains("/service-batch/"));
    }

    @Test
    void excludes_rabbitconfig_when_rabbitmq_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setRabbitmq(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().endsWith("RabbitConfig.java"));
    }

    @Test
    void excludes_redisconfig_when_redis_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setRedis(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().endsWith("RedisConfig.java"));
    }

    @Test
    void excludes_websocketconfig_when_websocket_disabled() {
        FeatureOptions f = new FeatureOptions();
        f.setWebsocket(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(f));
        assertThat(result).noneMatch(e -> e.path().endsWith("WebSocketConfig.java"));
        assertThat(result).noneMatch(e -> e.path().endsWith("batch-notifications.html"));
    }

    @Test
    void always_keeps_docker_compose_and_other_root_files() {
        FeatureOptions none = new FeatureOptions();
        none.setKeycloak(false); none.setAdmin(false); none.setRabbitmq(false);
        none.setRedis(false); none.setWebsocket(false);
        List<GeneratedFile> result = processor.process(sampleFiles(), ctxWithFeatures(none));
        assertThat(result).anyMatch(e -> e.path().endsWith("docker-compose.yml"));
    }
}
```

- [ ] **Lancer le test (doit échouer)**

```bash
mvn test -Dtest=FeatureFilterProcessorTest
```

- [ ] **Créer FeatureFilterProcessor**

```java
// src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.dto.FeatureOptions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Order(20)
public class FeatureFilterProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        FeatureOptions f = ctx.getRequest().getFeatures();
        BatchOptions b = ctx.getRequest().getBatch();
        String root = ctx.getTargetRoot();
        return files.stream()
            .filter(e -> include(e.path(), root, f, b))
            .toList();
    }

    private boolean include(String path, String root, FeatureOptions f, BatchOptions b) {
        String rel = relative(path, root);
        if (!f.isKeycloak()   && rel.startsWith("keycloak/"))                          return false;
        if (!f.isAdmin()      && rel.startsWith("ms-admin/"))                          return false;
        if (!f.isGrafana()    && rel.startsWith("observability/grafana/"))             return false;
        if (!f.isLoki()       && (rel.startsWith("observability/loki/")
                               || rel.startsWith("observability/promtail/")))          return false;
        if ((!f.isRabbitmq() || !b.isEnabled()) && rel.startsWith("service-batch/"))  return false;
        if (!f.isRabbitmq()   && (contains(rel, "/RabbitConfig.java")
                               || contains(rel, "/BatchNotificationListener.java")))   return false;
        if (!f.isRedis()      && (contains(rel, "/RedisConfig.java")
                               || contains(rel, "/RedisJobStore.java")
                               || contains(rel, "/RedisKeys.java")))                   return false;
        if (!f.isWebsocket()  && (contains(rel, "/WebSocketConfig.java")
                               || rel.endsWith("batch-notifications.html")))           return false;
        return true;
    }

    private String relative(String path, String root) {
        String prefix = root + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private boolean contains(String rel, String fragment) {
        return rel.contains(fragment);
    }
}
```

- [ ] **Lancer les tests (doivent passer)**

```bash
mvn test -Dtest=FeatureFilterProcessorTest
```
Attendu : `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Lancer tous les tests**

```bash
mvn test
```
Attendu : tous les tests passent.

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java
git commit -m "feat: add FeatureFilterProcessor"
```

---

## Task 5 — PackagePlaceholderProcessor

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessorTest.java`

- [ ] **Écrire les tests**

```java
// src/test/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessorTest.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class PackagePlaceholderProcessorTest {

    private final PackagePlaceholderProcessor processor = new PackagePlaceholderProcessor();

    @Test
    void replaces_basePackage_in_content() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/Foo.java",
                "package com.mr486.msplatform.servicea;")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).contains("com.example.myplatform.servicea");
        assertThat(contentOf(result.get(0))).doesNotContain("com.mr486.msplatform");
    }

    @Test
    void replaces_groupId_in_content_but_not_where_basePackage_already_replaced() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/pom.xml",
                "<groupId>com.mr486</groupId><parent><groupId>com.mr486</groupId></parent>")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).doesNotContain("com.mr486");
        assertThat(contentOf(result.get(0))).contains("com.example");
    }

    @Test
    void replaces_basePackage_in_path() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/src/main/java/com/mr486/msplatform/servicea/Foo.java", "content")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).path())
            .contains("com/example/myplatform/servicea/Foo.java");
    }

    @Test
    void replaces_groupId_in_path_when_not_covered_by_basePackage() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        // path where groupId path appears but not the full basePackage path
        List<GeneratedFile> input = List.of(
            file("ms-platform/eureka/src/main/java/com/mr486/eureka/Foo.java", "content")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).path()).contains("com/example/eureka/Foo.java");
    }

    @Test
    void replaces_java_version_in_pom() {
        GenerationContext ctx = GenerationContext.from(
            new com.mr486.generator.dto.PlatformGenerationRequest() {{ setJavaVersion("21"); }}
        );
        List<GeneratedFile> input = List.of(
            file("ms-platform/pom.xml", "<java.version>17</java.version>")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).contains("<java.version>21</java.version>");
    }

    @Test
    void is_noop_when_defaults_unchanged() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = List.of(
            file("ms-platform/service-a/Foo.java", "package com.mr486.msplatform.servicea;")
        );
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).isEqualTo("package com.mr486.msplatform.servicea;");
    }

    @Test
    void does_not_corrupt_binary_like_content() {
        GenerationContext ctx = ctxWithPackage("com.example", "com.example.myplatform");
        byte[] binaryContent = new byte[]{0, 1, 2, (byte) 0xFF};
        List<GeneratedFile> input = List.of(new GeneratedFile("ms-platform/some.bin", binaryContent, false));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result.get(0).content()).isEqualTo(binaryContent);
    }
}
```

- [ ] **Lancer le test (doit échouer)**

```bash
mvn test -Dtest=PackagePlaceholderProcessorTest
```

- [ ] **Créer PackagePlaceholderProcessor**

```java
// src/main/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessor.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(30)
public class PackagePlaceholderProcessor implements FileProcessor {

    private static final String SRC_BASE_PKG  = "com.mr486.msplatform";
    private static final String SRC_GROUP_ID  = "com.mr486";
    private static final String SRC_BASE_PATH = "com/mr486/msplatform/";
    private static final String SRC_GID_PATH  = "com/mr486/";
    private static final String SRC_JAVA_VER  = "<java.version>17</java.version>";

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        String tgtBasePkg  = ctx.getRequest().getBasePackage();
        String tgtGroupId  = ctx.getRequest().getGroupId();
        String tgtJavaVer  = ctx.getRequest().getJavaVersion();
        String tgtBasePath = tgtBasePkg.replace('.', '/') + "/";
        String tgtGidPath  = tgtGroupId.replace('.', '/') + "/";

        boolean samePackage = SRC_BASE_PKG.equals(tgtBasePkg) && SRC_GROUP_ID.equals(tgtGroupId);
        boolean sameJava    = "17".equals(tgtJavaVer);
        if (samePackage && sameJava) return files;

        return files.stream()
            .map(f -> transform(f, tgtBasePkg, tgtGroupId, tgtBasePath, tgtGidPath, tgtJavaVer, sameJava))
            .toList();
    }

    private GeneratedFile transform(GeneratedFile f,
                                    String tgtBasePkg, String tgtGroupId,
                                    String tgtBasePath, String tgtGidPath,
                                    String tgtJavaVer, boolean sameJava) {
        String newPath = transformPath(f.path(), tgtBasePath, tgtGidPath);
        byte[] newContent = transformContent(f.content(), tgtBasePkg, tgtGroupId, tgtJavaVer, sameJava);
        return new GeneratedFile(newPath, newContent, f.executable());
    }

    private String transformPath(String path, String tgtBasePath, String tgtGidPath) {
        // longer first to avoid partial replacement
        path = path.replace(SRC_BASE_PATH, tgtBasePath);
        path = path.replace(SRC_GID_PATH, tgtGidPath);
        return path;
    }

    private byte[] transformContent(byte[] content, String tgtBasePkg, String tgtGroupId,
                                    String tgtJavaVer, boolean sameJava) {
        if (containsNullByte(content)) return content;
        try {
            String text = new String(content, StandardCharsets.UTF_8);
            // longer first
            text = text.replace(SRC_BASE_PKG, tgtBasePkg);
            text = text.replace(SRC_GROUP_ID, tgtGroupId);
            if (!sameJava) {
                text = text.replace(SRC_JAVA_VER, "<java.version>" + tgtJavaVer + "</java.version>");
            }
            return text.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return content;
        }
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
```

- [ ] **Lancer les tests (doivent passer)**

```bash
mvn test -Dtest=PackagePlaceholderProcessorTest
```
Attendu : `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Lancer tous les tests**

```bash
mvn test
```

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessor.java src/test/java/com/mr486/generator/pipeline/processor/PackagePlaceholderProcessorTest.java
git commit -m "feat: add PackagePlaceholderProcessor"
```

---

## Task 6 — BatchConfigProcessor

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/processor/BatchConfigProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/BatchConfigProcessorTest.java`

- [ ] **Écrire les tests**

```java
// src/test/java/com/mr486/generator/pipeline/processor/BatchConfigProcessorTest.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class BatchConfigProcessorTest {

    private final BatchConfigProcessor processor = new BatchConfigProcessor();

    private static final String SAMPLE_ENV =
        "BATCH_REPLICAS=4\n" +
        "BATCH_FILE_CONCURRENCY=5\n" +
        "BATCH_MIN_DELAY_MS=500\n" +
        "BATCH_MAX_DELAY_MS=1500\n" +
        "BATCH_MEMORY_LIMIT=768m\n";

    private GenerationContext ctxWithBatch(int replicas, int concurrency,
                                           long minDelay, long maxDelay, String memory) {
        BatchOptions b = new BatchOptions();
        b.setReplicas(replicas);
        b.setFileConcurrency(concurrency);
        b.setMinDelayMs(minDelay);
        b.setMaxDelayMs(maxDelay);
        b.setMemoryLimit(memory);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setBatch(b);
        return GenerationContext.from(req);
    }

    @Test
    void replaces_all_batch_values_in_env_file() {
        GenerationContext ctx = ctxWithBatch(8, 10, 200, 800, "512m");
        List<GeneratedFile> result = processor.process(
            List.of(file("ms-platform/.env", SAMPLE_ENV)), ctx);
        String content = contentOf(result.get(0));
        assertThat(content).contains("BATCH_REPLICAS=8");
        assertThat(content).contains("BATCH_FILE_CONCURRENCY=10");
        assertThat(content).contains("BATCH_MIN_DELAY_MS=200");
        assertThat(content).contains("BATCH_MAX_DELAY_MS=800");
        assertThat(content).contains("BATCH_MEMORY_LIMIT=512m");
    }

    @Test
    void is_noop_when_batch_disabled() {
        BatchOptions b = new BatchOptions();
        b.setEnabled(false);
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setBatch(b);
        GenerationContext ctx = GenerationContext.from(req);
        List<GeneratedFile> input = List.of(file("ms-platform/.env", SAMPLE_ENV));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).isEqualTo(SAMPLE_ENV);
    }

    @Test
    void is_noop_when_defaults_unchanged() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = List.of(file("ms-platform/.env", SAMPLE_ENV));
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(contentOf(result.get(0))).isEqualTo(SAMPLE_ENV);
    }
}
```

- [ ] **Lancer le test (doit échouer)**

```bash
mvn test -Dtest=BatchConfigProcessorTest
```

- [ ] **Créer BatchConfigProcessor**

```java
// src/main/java/com/mr486/generator/pipeline/processor/BatchConfigProcessor.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.BatchOptions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(40)
public class BatchConfigProcessor implements FileProcessor {

    private static final BatchOptions DEFAULTS = new BatchOptions();

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        BatchOptions b = ctx.getRequest().getBatch();
        if (!b.isEnabled()) return files;
        if (isDefault(b)) return files;
        return files.stream().map(f -> replace(f, b)).toList();
    }

    private GeneratedFile replace(GeneratedFile f, BatchOptions b) {
        if (containsNullByte(f.content())) return f;
        try {
            String text = new String(f.content(), StandardCharsets.UTF_8);
            text = text.replace("BATCH_REPLICAS="      + DEFAULTS.getReplicas(),      "BATCH_REPLICAS="      + b.getReplicas());
            text = text.replace("BATCH_FILE_CONCURRENCY=" + DEFAULTS.getFileConcurrency(), "BATCH_FILE_CONCURRENCY=" + b.getFileConcurrency());
            text = text.replace("BATCH_MIN_DELAY_MS="  + DEFAULTS.getMinDelayMs(),    "BATCH_MIN_DELAY_MS="  + b.getMinDelayMs());
            text = text.replace("BATCH_MAX_DELAY_MS="  + DEFAULTS.getMaxDelayMs(),    "BATCH_MAX_DELAY_MS="  + b.getMaxDelayMs());
            text = text.replace("BATCH_MEMORY_LIMIT="  + DEFAULTS.getMemoryLimit(),   "BATCH_MEMORY_LIMIT="  + b.getMemoryLimit());
            return new GeneratedFile(f.path(), text.getBytes(StandardCharsets.UTF_8), f.executable());
        } catch (Exception e) {
            return f;
        }
    }

    private boolean isDefault(BatchOptions b) {
        return b.getReplicas()       == DEFAULTS.getReplicas()
            && b.getFileConcurrency() == DEFAULTS.getFileConcurrency()
            && b.getMinDelayMs()      == DEFAULTS.getMinDelayMs()
            && b.getMaxDelayMs()      == DEFAULTS.getMaxDelayMs()
            && b.getMemoryLimit().equals(DEFAULTS.getMemoryLimit());
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
```

- [ ] **Lancer les tests (doivent passer)**

```bash
mvn test -Dtest=BatchConfigProcessorTest
```
Attendu : `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Lancer tous les tests**

```bash
mvn test
```

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/BatchConfigProcessor.java src/test/java/com/mr486/generator/pipeline/processor/BatchConfigProcessorTest.java
git commit -m "feat: add BatchConfigProcessor"
```

---

## Task 7 — ResourceExpandProcessor : clonage de base

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java`

Note : `databaseType` et `idType` sont traités dans les tâches 8, 9, 10. Cette tâche implémente le clonage + remplacement de noms (POSTGRES/LONG par défaut).

- [ ] **Écrire les tests de clonage de base**

```java
// src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.*;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;

class ResourceExpandProcessorTest {

    private final ResourceExpandProcessor processor = new ResourceExpandProcessor();

    // Simule les fichiers service-a pertinents (après PackagePlaceholderProcessor)
    private List<GeneratedFile> serviceAFiles(String root) {
        String base = root + "/service-a/";
        String javaPkg = base + "src/main/java/com/mr486/msplatform/servicea/";
        return new ArrayList<>(List.of(
            file(base + "pom.xml", "<artifactId>service-a</artifactId><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency><dependency><groupId>org.liquibase</groupId><artifactId>liquibase-core</artifactId></dependency>"),
            file(base + "Dockerfile", "FROM eclipse-temurin:17-jre-jammy"),
            file(javaPkg + "ServiceAApplication.java", "package com.mr486.msplatform.servicea;\npublic class ServiceAApplication{}"),
            file(javaPkg + "entity/ResourceA.java", "package com.mr486.msplatform.servicea.entity;\nimport jakarta.persistence.*;\n@Entity @Table(name=\"resources_a\")\npublic class ResourceA{ @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; private String name; }"),
            file(javaPkg + "repository/ResourceARepository.java", "package com.mr486.msplatform.servicea.repository;\nimport org.springframework.data.jpa.repository.JpaRepository;\npublic interface ResourceARepository extends JpaRepository<ResourceA,Long> {}"),
            file(javaPkg + "dto/ResourceADto.java", "package com.mr486.msplatform.servicea.dto;\npublic class ResourceADto{ private Long id; private String name; }"),
            file(javaPkg + "controller/ResourceAController.java", "package com.mr486.msplatform.servicea.controller;\n@RequestMapping(\"/api/resources-a\")\npublic class ResourceAController{}"),
            file(javaPkg + "service/ResourceAService.java", "package com.mr486.msplatform.servicea.service;\npublic class ResourceAService{}"),
            file(base + "src/main/resources/application.yml",
                "spring:\n  application:\n    name: service-a\n  datasource:\n    url: ${SERVICE_A_DATASOURCE_URL:jdbc:postgresql://localhost:5432/service_a_db}\n    driver-class-name: org.postgresql.Driver"),
            file(base + "src/main/resources/db/changelog/001-init.sql",
                "CREATE TABLE IF NOT EXISTS resources_a (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(100) NOT NULL);"),
            // fichiers patch à ne pas cloner
            file(root + "/service-a/service-batch/pom.xml", "patch"),
            file(root + "/service-a/service-consumer/pom.xml", "patch"),
            // service-b et service-c à supprimer si resources non-vide
            file(root + "/service-b/pom.xml", "<artifactId>service-b</artifactId>"),
            file(root + "/service-c/pom.xml", "<artifactId>service-c</artifactId>")
        ));
    }

    private ResourceModuleRequest resource(String serviceName, String className, String route,
                                           DatabaseType db, IdType id) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(serviceName); r.setClassName(className);
        r.setRoutePrefix(route); r.setDatabaseType(db); r.setIdType(id);
        return r;
    }

    private GenerationContext ctxWithResources(List<ResourceModuleRequest> resources) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(resources);
        return GenerationContext.from(req);
    }

    @Test
    void noop_when_resources_empty() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = serviceAFiles("ms-platform");
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result).hasSize(input.size());
    }

    @Test
    void removes_default_services_when_resources_provided() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).noneMatch(f -> f.path().contains("/service-a/") && !f.path().contains("/service-a/service-batch/") && !f.path().contains("/service-a/service-consumer/"));
        assertThat(result).noneMatch(f -> f.path().contains("/service-b/"));
        assertThat(result).noneMatch(f -> f.path().contains("/service-c/"));
    }

    @Test
    void generates_service_with_correct_name_in_paths() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).anyMatch(f -> f.path().contains("/invoice/"));
        assertThat(result).anyMatch(f -> f.path().contains("/invoice/") && f.path().endsWith("Application.java"));
    }

    @Test
    void replaces_ResourceA_with_className_in_content() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        result.stream()
            .filter(f -> f.path().contains("/invoice/") && f.path().endsWith(".java"))
            .forEach(f -> {
                assertThat(contentOf(f)).doesNotContain("ResourceA");
                assertThat(contentOf(f)).doesNotContain("service-a");
            });
    }

    @Test
    void generates_multiple_services() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG),
            resource("product", "Product", "/api/products", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).anyMatch(f -> f.path().contains("/invoice/"));
        assertThat(result).anyMatch(f -> f.path().contains("/product/"));
    }

    @Test
    void does_not_clone_patch_subdirs() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        // Les fichiers patch service-a/service-batch/ et service-a/service-consumer/ doivent rester tels quels
        // mais ne doivent pas être clonés vers invoice/
        assertThat(result).noneMatch(f -> f.path().startsWith("ms-platform/invoice/service-batch/"));
        assertThat(result).noneMatch(f -> f.path().startsWith("ms-platform/invoice/service-consumer/"));
    }
}
```

- [ ] **Lancer le test (doit échouer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest#noop_when_resources_empty,ResourceExpandProcessorTest#removes_default_services_when_resources_provided,ResourceExpandProcessorTest#generates_service_with_correct_name_in_paths
```

- [ ] **Créer ResourceExpandProcessor (clonage de base)**

```java
// src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(50)
public class ResourceExpandProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();
        if (resources == null || resources.isEmpty()) return files;

        String root = ctx.getTargetRoot();
        List<GeneratedFile> serviceATemplate = extractServiceATemplate(files, root);
        List<GeneratedFile> filtered = removeDefaultServices(files, root);

        List<GeneratedFile> result = new ArrayList<>(filtered);
        for (ResourceModuleRequest res : resources) {
            result.addAll(generateService(serviceATemplate, res, root));
        }
        return result;
    }

    // ── Extraction du template service-a ──────────────────────────────────────

    private List<GeneratedFile> extractServiceATemplate(List<GeneratedFile> files, String root) {
        String serviceAPrefix = root + "/service-a/";
        String patchBatch    = serviceAPrefix + "service-batch/";
        String patchConsumer = serviceAPrefix + "service-consumer/";
        return files.stream()
            .filter(f -> f.path().startsWith(serviceAPrefix))
            .filter(f -> !f.path().startsWith(patchBatch))
            .filter(f -> !f.path().startsWith(patchConsumer))
            .collect(Collectors.toList());
    }

    private List<GeneratedFile> removeDefaultServices(List<GeneratedFile> files, String root) {
        return files.stream()
            .filter(f -> !isDefaultService(f.path(), root))
            .collect(Collectors.toList());
    }

    private boolean isDefaultService(String path, String root) {
        String rel = relative(path, root);
        return rel.startsWith("service-a/")
            || rel.startsWith("service-b/")
            || rel.startsWith("service-c/");
    }

    // ── Génération d'un service ───────────────────────────────────────────────

    private List<GeneratedFile> generateService(List<GeneratedFile> template,
                                                ResourceModuleRequest res, String root) {
        List<GeneratedFile> generated = new ArrayList<>();
        for (GeneratedFile f : template) {
            String newPath    = transformPath(f.path(), res, root);
            byte[] newContent = transformContent(f.content(), res);
            newContent = applyDatabaseType(newPath, newContent, res);
            newContent = applyIdType(newPath, newContent, res);
            generated.add(new GeneratedFile(newPath, newContent, f.executable()));
        }
        return generated;
    }

    // ── Transformations de chemins ────────────────────────────────────────────

    private String transformPath(String path, ResourceModuleRequest res, String root) {
        String serviceClass = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        // Ordre : plus spécifique en premier
        path = path.replace(root + "/service-a/", root + "/" + res.getServiceName() + "/");
        path = path.replace("/servicea/", "/" + servicePackage + "/");
        path = path.replace("ServiceA", serviceClass);
        path = path.replace("ResourceA", res.getClassName());
        return path;
    }

    // ── Transformations de contenu ────────────────────────────────────────────

    private byte[] transformContent(byte[] content, ResourceModuleRequest res) {
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        text = applyBaseReplacements(text, res);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String applyBaseReplacements(String text, ResourceModuleRequest res) {
        String serviceClass   = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        String serviceSnake   = res.getServiceName().replace("-", "_");
        String serviceScream  = serviceSnake.toUpperCase();
        String entityPlural   = res.getClassName().toLowerCase() + "s";
        String entityLower    = res.getClassName().toLowerCase();

        // Ordre : plus long/spécifique en premier
        text = text.replace("USER_SERVICE_A",    "USER_" + serviceScream);
        text = text.replace("SERVICE_A",          serviceScream);
        text = text.replace("resources_a",        entityPlural);
        text = text.replace("resource_a",         entityLower);
        text = text.replace("/api/resources-a",   res.getRoutePrefix());
        text = text.replace("service-a",          res.getServiceName());
        text = text.replace("service_a",          serviceSnake);
        text = text.replace("servicea",           servicePackage);
        text = text.replace("ServiceA",           serviceClass);
        text = text.replace("ResourceA",          res.getClassName());
        return text;
    }

    // ── DatabaseType (délégué aux tâches 8 et 9) ─────────────────────────────

    protected byte[] applyDatabaseType(String path, byte[] content, ResourceModuleRequest res) {
        if (res.getDatabaseType() == null || res.getDatabaseType() == DatabaseType.POSTGRES) return content;
        // H2 et MONGO implémentés dans les tâches suivantes
        return content;
    }

    // ── IdType (délégué à la tâche 10) ───────────────────────────────────────

    protected byte[] applyIdType(String path, byte[] content, ResourceModuleRequest res) {
        if (res.getIdType() == null || res.getIdType() == IdType.LONG) return content;
        // INTEGER et UUID implémentés dans la tâche 10
        return content;
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    protected String toPascalCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("[-_]")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    protected String toConcatLower(String kebab) {
        return kebab.replace("-", "").replace("_", "").toLowerCase();
    }

    protected String relative(String path, String root) {
        String prefix = root + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    protected boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
```

- [ ] **Lancer les tests de base (doivent passer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest
```
Attendu : les 6 tests de base passent. Les tests `H2`, `MONGO`, `INTEGER`, `UUID` n'existent pas encore.

- [ ] **Lancer tous les tests**

```bash
mvn test
```

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java
git commit -m "feat: add ResourceExpandProcessor (base cloning)"
```

---

## Task 8 — ResourceExpandProcessor : databaseType H2

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java`

- [ ] **Ajouter les tests H2**

Ajouter dans `ResourceExpandProcessorTest.java` :

```java
    @Test
    void h2_replaces_postgres_driver_in_pom() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("inventory", "Item", "/api/items", DatabaseType.H2, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile pom = result.stream()
            .filter(f -> f.path().endsWith("inventory/pom.xml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(pom)).contains("h2");
        assertThat(contentOf(pom)).doesNotContain("postgresql");
    }

    @Test
    void h2_replaces_datasource_in_application_yml() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("inventory", "Item", "/api/items", DatabaseType.H2, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile yml = result.stream()
            .filter(f -> f.path().endsWith("inventory/src/main/resources/application.yml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(yml)).contains("jdbc:h2:mem:");
        assertThat(contentOf(yml)).contains("org.h2.Driver");
        assertThat(contentOf(yml)).doesNotContain("org.postgresql.Driver");
    }
```

- [ ] **Lancer les tests H2 (doivent échouer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest#h2_replaces_postgres_driver_in_pom,ResourceExpandProcessorTest#h2_replaces_datasource_in_application_yml
```

- [ ] **Implémenter H2 dans applyDatabaseType**

Remplacer la méthode `applyDatabaseType` dans `ResourceExpandProcessor.java` :

```java
    protected byte[] applyDatabaseType(String path, byte[] content, ResourceModuleRequest res) {
        if (res.getDatabaseType() == null || res.getDatabaseType() == DatabaseType.POSTGRES) return content;
        if (res.getDatabaseType() == DatabaseType.H2)    return applyH2(path, content, res);
        if (res.getDatabaseType() == DatabaseType.MONGO) return applyMongo(path, content, res);
        return content;
    }

    private byte[] applyH2(String path, byte[] content, ResourceModuleRequest res) {
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        String dbName = res.getServiceName().replace("-", "") + "db";

        if (path.endsWith("pom.xml")) {
            text = text.replace(
                "<groupId>org.postgresql</groupId><artifactId>postgresql</artifactId>",
                "<groupId>com.h2database</groupId><artifactId>h2</artifactId>"
            );
        }
        if (path.endsWith("application.yml")) {
            String serviceSnake = res.getServiceName().replace("-", "_").toUpperCase();
            text = text.replace(
                "url: ${" + serviceSnake + "_DATASOURCE_URL:jdbc:postgresql://localhost:5432/" + res.getServiceName().replace("-","_") + "_db}",
                "url: jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            );
            text = text.replace(
                "username: ${" + serviceSnake + "_DB_USERNAME:" + res.getServiceName().replace("-","_") + "}",
                "username: sa"
            );
            text = text.replace(
                "password: ${" + serviceSnake + "_DB_PASSWORD:" + res.getServiceName().replace("-","_") + "}",
                "password:"
            );
            text = text.replace("driver-class-name: org.postgresql.Driver", "driver-class-name: org.h2.Driver");
            // Ajouter la console H2 après la datasource block
            if (!text.contains("h2:") && text.contains("driver-class-name: org.h2.Driver")) {
                text = text.replace("driver-class-name: org.h2.Driver",
                    "driver-class-name: org.h2.Driver\n  h2:\n    console:\n      enabled: true");
            }
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] applyMongo(String path, byte[] content, ResourceModuleRequest res) {
        // Implémenté dans la tâche 9
        return content;
    }
```

**Note :** La méthode `applyDatabaseType` est déclarée `protected` dans Task 7. Remplacer son contenu et ajouter les deux méthodes privées `applyH2` et `applyMongo`.

- [ ] **Lancer les tests H2 (doivent passer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest
```
Attendu : tous les tests existants passent, y compris les 2 nouveaux.

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java
git commit -m "feat: ResourceExpandProcessor - H2 database type"
```

---

## Task 9 — ResourceExpandProcessor : databaseType MongoDB

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java`

Pour MongoDB, les transformations nécessaires par rapport à service-a (PostgreSQL/JPA) sont :
- `entity/` → `document/` dans le path
- Contenu de l'entité : JPA → MongoDB document
- Repository : `JpaRepository` → `MongoRepository`, type ID `Long` → `String`
- `pom.xml` : retirer JPA/Postgres/Liquibase, ajouter MongoDB/Mongock
- `application.yml` : retirer datasource/jpa/liquibase, ajouter MongoDB URI
- Supprimer les fichiers `db/changelog/`

- [ ] **Ajouter les tests MongoDB**

Ajouter dans `ResourceExpandProcessorTest.java` :

```java
    @Test
    void mongo_renames_entity_dir_to_document() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).anyMatch(f -> f.path().contains("/catalog/") && f.path().contains("/document/"));
        assertThat(result).noneMatch(f -> f.path().contains("/catalog/") && f.path().contains("/entity/"));
    }

    @Test
    void mongo_entity_uses_document_annotation() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile doc = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().contains("/document/Product.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(doc)).contains("@Document");
        assertThat(contentOf(doc)).doesNotContain("@Entity");
        assertThat(contentOf(doc)).contains("private String id");
    }

    @Test
    void mongo_repository_extends_MongoRepository() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile repo = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().endsWith("Repository.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(repo)).contains("MongoRepository");
        assertThat(contentOf(repo)).doesNotContain("JpaRepository");
    }

    @Test
    void mongo_pom_contains_mongodb_not_jpa() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile pom = result.stream()
            .filter(f -> f.path().endsWith("catalog/pom.xml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(pom)).contains("spring-boot-starter-data-mongodb");
        assertThat(contentOf(pom)).doesNotContain("spring-boot-starter-data-jpa");
        assertThat(contentOf(pom)).doesNotContain("postgresql");
    }

    @Test
    void mongo_application_yml_has_mongodb_uri_not_datasource() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile yml = result.stream()
            .filter(f -> f.path().endsWith("catalog/src/main/resources/application.yml"))
            .findFirst().orElseThrow();
        assertThat(contentOf(yml)).contains("mongodb:");
        assertThat(contentOf(yml)).doesNotContain("datasource:");
    }

    @Test
    void mongo_removes_db_changelog_files() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).noneMatch(f -> f.path().contains("/catalog/") && f.path().contains("/db/changelog/"));
    }
```

- [ ] **Lancer les tests MongoDB (doivent échouer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest#mongo_renames_entity_dir_to_document,ResourceExpandProcessorTest#mongo_entity_uses_document_annotation
```

- [ ] **Implémenter applyMongo dans ResourceExpandProcessor**

Les templates de contenu MongoDB pour l'entité et le repository sont définis comme constantes dans la classe. Ajouter les constantes et remplacer `applyMongo` :

```java
    // ── Templates MongoDB ─────────────────────────────────────────────────────

    private static final String MONGO_ENTITY_TEMPLATE =
        "package {PKG}.document;\n" +
        "import lombok.*;\n" +
        "import org.springframework.data.annotation.Id;\n" +
        "import org.springframework.data.mongodb.core.mapping.Document;\n" +
        "@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder\n" +
        "@Document(collection=\"{COLLECTION}\")\n" +
        "public class {CLASS}{ @Id private String id; private String name; private String description; }";

    private static final String MONGO_REPO_TEMPLATE =
        "package {PKG}.repository;\n" +
        "import {PKG}.document.{CLASS};\n" +
        "import org.springframework.stereotype.Repository;\n" +
        "import org.springframework.data.mongodb.repository.MongoRepository;\n" +
        "@Repository\n" +
        "public interface {CLASS}Repository extends MongoRepository<{CLASS},String> {}";

    private static final String MONGO_APP_YML_TEMPLATE =
        "server:\n" +
        "  port: ${{{SERVICE_UPPER}_PORT:8080}}\n" +
        "spring:\n" +
        "  application:\n" +
        "    name: {SERVICE_NAME}\n" +
        "  data:\n" +
        "    mongodb:\n" +
        "      uri: ${{{SERVICE_UPPER}_MONGO_URI:mongodb://{SERVICE_SNAKE}:{SERVICE_SNAKE}@localhost:27017/{SERVICE_SNAKE}_db?authSource=admin}}\n" +
        "  security:\n" +
        "    oauth2:\n" +
        "      resourceserver:\n" +
        "        jwt:\n" +
        "          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8089/realms/ms-realm}\n" +
        "          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://keycloak:8080/realms/ms-realm/protocol/openid-connect/certs}\n" +
        "eureka:\n" +
        "  client:\n" +
        "    service-url:\n" +
        "      defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}\n" +
        "management:\n" +
        "  endpoints:\n" +
        "    web:\n" +
        "      exposure:\n" +
        "        include: health,info\n";

    private static final String MONGO_POM_DEPS =
        "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-mongodb</artifactId></dependency>" +
        "<dependency><groupId>io.mongock</groupId><artifactId>mongock-springboot-v3</artifactId></dependency>" +
        "<dependency><groupId>io.mongock</groupId><artifactId>mongodb-springdata-v4-driver</artifactId></dependency>";
```

Puis remplacer `applyMongo` :

```java
    private byte[] applyMongo(String path, byte[] content, ResourceModuleRequest res) {
        String serviceClass   = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        String serviceSnake   = res.getServiceName().replace("-", "_");
        String serviceUpper   = serviceSnake.toUpperCase();
        String collection     = res.getClassName().toLowerCase() + "s";

        // Supprimer les fichiers changelog Liquibase
        if (path.contains("/db/changelog/")) return null;  // sera filtré (voir generateService)

        if (path.endsWith("application.yml")) {
            String yml = MONGO_APP_YML_TEMPLATE
                .replace("{SERVICE_UPPER}", serviceUpper)
                .replace("{SERVICE_NAME}",  res.getServiceName())
                .replace("{SERVICE_SNAKE}",  serviceSnake);
            return yml.getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("pom.xml") && !containsNullByte(content)) {
            String text = new String(content, StandardCharsets.UTF_8);
            text = text.replace(
                "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>",
                MONGO_POM_DEPS);
            text = text.replace(
                "<dependency><groupId>org.liquibase</groupId><artifactId>liquibase-core</artifactId></dependency>", "");
            text = text.replace(
                "<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>", "");
            return text.getBytes(StandardCharsets.UTF_8);
        }
        // Fichier entity → contenu MongoDB
        if (path.contains("/entity/") && path.endsWith(".java") && !containsNullByte(content)) {
            String pkg = res.getRequest() != null
                ? res.getRequest().getBasePackage() + "." + servicePackage
                : "com.mr486.msplatform." + servicePackage;
            String doc = MONGO_ENTITY_TEMPLATE
                .replace("{PKG}", pkg)
                .replace("{COLLECTION}", collection)
                .replace("{CLASS}", res.getClassName());
            return doc.getBytes(StandardCharsets.UTF_8);
        }
        // Fichier repository → MongoRepository
        if (path.contains("/repository/") && path.endsWith("Repository.java") && !containsNullByte(content)) {
            String pkg = res.getRequest() != null
                ? res.getRequest().getBasePackage() + "." + servicePackage
                : "com.mr486.msplatform." + servicePackage;
            String repo = MONGO_REPO_TEMPLATE
                .replace("{PKG}", pkg)
                .replace("{CLASS}", res.getClassName());
            return repo.getBytes(StandardCharsets.UTF_8);
        }
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        return text.getBytes(StandardCharsets.UTF_8);
    }
```

**Note importante :** `applyMongo` retourne `null` pour les fichiers `db/changelog/`. Modifier `generateService` pour filtrer les `null` :

```java
    private List<GeneratedFile> generateService(List<GeneratedFile> template,
                                                ResourceModuleRequest res, String root) {
        List<GeneratedFile> generated = new ArrayList<>();
        for (GeneratedFile f : template) {
            String newPath    = transformPath(f.path(), res, root);
            byte[] newContent = transformContent(f.content(), res);
            newContent = applyDatabaseType(newPath, newContent, res);
            if (newContent == null) continue;  // fichier supprimé (ex: changelog pour Mongo)
            newPath    = applyMongoPathRename(newPath, res);
            newContent = applyIdType(newPath, newContent, res);
            generated.add(new GeneratedFile(newPath, newContent, f.executable()));
        }
        return generated;
    }
```

Ajouter la méthode `applyMongoPathRename` :

```java
    private String applyMongoPathRename(String path, ResourceModuleRequest res) {
        if (res.getDatabaseType() == DatabaseType.MONGO) {
            path = path.replace("/entity/", "/document/");
        }
        return path;
    }
```

**Note :** `ResourceModuleRequest` a besoin d'un accès au `request` pour obtenir le `basePackage`. Modifier `generateService` pour passer le contexte ou résoudre le `basePackage` directement. La solution la plus simple : passer `ctx` à `generateService` :

```java
    // Modifier la signature dans process() et generateService()
    private List<GeneratedFile> generateService(List<GeneratedFile> template,
                                                ResourceModuleRequest res,
                                                String root,
                                                GenerationContext ctx) {
        // ... même code mais passer ctx.getRequest().getBasePackage() à applyMongo
    }
```

Et dans `applyMongo`, remplacer les occurrences de `res.getRequest()` par le paramètre `basePackage` passé directement.

**Refactorer `applyMongo` pour accepter `basePackage` en paramètre :**

```java
    private byte[] applyMongo(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        String servicePackage = toConcatLower(res.getServiceName());
        String serviceSnake   = res.getServiceName().replace("-", "_");
        String serviceUpper   = serviceSnake.toUpperCase();
        String collection     = res.getClassName().toLowerCase() + "s";
        String pkg            = basePackage + "." + servicePackage;

        if (path.contains("/db/changelog/")) return null;
        if (path.endsWith("application.yml")) {
            String yml = MONGO_APP_YML_TEMPLATE
                .replace("{SERVICE_UPPER}", serviceUpper)
                .replace("{SERVICE_NAME}",  res.getServiceName())
                .replace("{SERVICE_SNAKE}", serviceSnake);
            return yml.getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("pom.xml") && !containsNullByte(content)) {
            String text = new String(content, StandardCharsets.UTF_8);
            text = text.replace("<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>", MONGO_POM_DEPS);
            text = text.replace("<dependency><groupId>org.liquibase</groupId><artifactId>liquibase-core</artifactId></dependency>", "");
            text = text.replace("<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>", "");
            return text.getBytes(StandardCharsets.UTF_8);
        }
        if (path.contains("/entity/") && path.endsWith(".java")) {
            String doc = MONGO_ENTITY_TEMPLATE
                .replace("{PKG}", pkg)
                .replace("{COLLECTION}", collection)
                .replace("{CLASS}", res.getClassName());
            return doc.getBytes(StandardCharsets.UTF_8);
        }
        if (path.contains("/repository/") && path.endsWith("Repository.java")) {
            String repo = MONGO_REPO_TEMPLATE
                .replace("{PKG}", pkg)
                .replace("{CLASS}", res.getClassName());
            return repo.getBytes(StandardCharsets.UTF_8);
        }
        if (containsNullByte(content)) return content;
        return new String(content, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
    }
```

Mettre à jour `applyDatabaseType` pour accepter `basePackage` :

```java
    protected byte[] applyDatabaseType(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (res.getDatabaseType() == null || res.getDatabaseType() == DatabaseType.POSTGRES) return content;
        if (res.getDatabaseType() == DatabaseType.H2)    return applyH2(path, content, res);
        if (res.getDatabaseType() == DatabaseType.MONGO) return applyMongo(path, content, res, basePackage);
        return content;
    }
```

Et mettre à jour `generateService` pour passer `ctx.getRequest().getBasePackage()` :

```java
    private List<GeneratedFile> generateService(List<GeneratedFile> template,
                                                ResourceModuleRequest res,
                                                String root,
                                                GenerationContext ctx) {
        String basePackage = ctx.getRequest().getBasePackage();
        List<GeneratedFile> generated = new ArrayList<>();
        for (GeneratedFile f : template) {
            String newPath    = transformPath(f.path(), res, root);
            byte[] newContent = transformContent(f.content(), res);
            newContent = applyDatabaseType(newPath, newContent, res, basePackage);
            if (newContent == null) continue;
            newPath    = applyMongoPathRename(newPath, res);
            newContent = applyIdType(newPath, newContent, res);
            generated.add(new GeneratedFile(newPath, newContent, f.executable()));
        }
        return generated;
    }
```

Mettre à jour l'appel dans `process` :

```java
        for (ResourceModuleRequest res : resources) {
            result.addAll(generateService(serviceATemplate, res, root, ctx));
        }
```

- [ ] **Lancer les tests MongoDB (doivent passer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest
```
Attendu : tous les tests passent.

- [ ] **Lancer tous les tests**

```bash
mvn test
```

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java
git commit -m "feat: ResourceExpandProcessor - MongoDB database type"
```

---

## Task 10 — ResourceExpandProcessor : idType INTEGER et UUID

**Files:**
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java`

Remplacements ciblés dans les fichiers JPA (POSTGRES et H2 uniquement — MONGO utilise toujours `String`).

- [ ] **Ajouter les tests idType**

Ajouter dans `ResourceExpandProcessorTest.java` :

```java
    @Test
    void integer_replaces_Long_with_Integer_in_entity() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.INTEGER)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile entity = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().contains("/entity/"))
            .findFirst().orElseThrow();
        assertThat(contentOf(entity)).contains("private Integer id");
        assertThat(contentOf(entity)).doesNotContain("private Long id");
    }

    @Test
    void integer_replaces_Long_in_repository_generic() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.INTEGER)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile repo = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("Repository.java"))
            .findFirst().orElseThrow();
        assertThat(contentOf(repo)).contains("JpaRepository<Order,Integer>");
        assertThat(contentOf(repo)).doesNotContain("JpaRepository<Order,Long>");
    }

    @Test
    void uuid_adds_UUID_type_and_generation_strategy() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile entity = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().contains("/entity/"))
            .findFirst().orElseThrow();
        assertThat(contentOf(entity)).contains("private UUID id");
        assertThat(contentOf(entity)).contains("GenerationType.UUID");
        assertThat(contentOf(entity)).contains("import java.util.UUID");
    }

    @Test
    void uuid_replaces_BIGINT_with_UUID_in_sql() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("order", "Order", "/api/orders", DatabaseType.POSTGRES, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile sql = result.stream()
            .filter(f -> f.path().contains("/order/") && f.path().endsWith("001-init.sql"))
            .findFirst().orElseThrow();
        assertThat(contentOf(sql)).contains("UUID DEFAULT gen_random_uuid() PRIMARY KEY");
        assertThat(contentOf(sql)).doesNotContain("BIGINT");
    }

    @Test
    void mongo_idType_is_always_string_regardless_of_request() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("catalog", "Product", "/api/products", DatabaseType.MONGO, IdType.UUID)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        GeneratedFile doc = result.stream()
            .filter(f -> f.path().contains("/catalog/") && f.path().contains("/document/"))
            .findFirst().orElseThrow();
        assertThat(contentOf(doc)).contains("private String id");
        assertThat(contentOf(doc)).doesNotContain("private UUID id");
    }
```

- [ ] **Lancer les tests idType (doivent échouer)**

```bash
mvn test -Dtest=ResourceExpandProcessorTest#integer_replaces_Long_with_Integer_in_entity,ResourceExpandProcessorTest#uuid_adds_UUID_type_and_generation_strategy
```

- [ ] **Implémenter applyIdType dans ResourceExpandProcessor**

Remplacer la méthode `applyIdType` :

```java
    protected byte[] applyIdType(String path, byte[] content, ResourceModuleRequest res) {
        if (res.getIdType() == null || res.getIdType() == IdType.LONG) return content;
        // MONGO gère toujours String, idType ignoré
        if (res.getDatabaseType() == DatabaseType.MONGO) return content;
        if (containsNullByte(content)) return content;

        String text = new String(content, StandardCharsets.UTF_8);
        if (res.getIdType() == IdType.INTEGER) text = applyIntegerType(text, path, res);
        if (res.getIdType() == IdType.UUID)    text = applyUuidType(text, path, res);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String applyIntegerType(String text, String path, ResourceModuleRequest res) {
        // Entity : Long id → Integer id
        text = text.replace("private Long id", "private Integer id");
        // Repository generic
        text = text.replace("JpaRepository<" + res.getClassName() + ",Long>",
                            "JpaRepository<" + res.getClassName() + ",Integer>");
        // DTO
        text = text.replace("private Long id", "private Integer id");
        return text;
    }

    private String applyUuidType(String text, String path, ResourceModuleRequest res) {
        // Entity
        text = text.replace(
            "@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id",
            "@Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id");
        // Ajouter l'import UUID si ce n'est pas déjà là
        if (text.contains("private UUID id") && !text.contains("import java.util.UUID")) {
            text = text.replace("import jakarta.persistence.*;", "import jakarta.persistence.*;\nimport java.util.UUID;");
        }
        // Repository generic
        text = text.replace("JpaRepository<" + res.getClassName() + ",Long>",
                            "JpaRepository<" + res.getClassName() + ",UUID>");
        // DTO
        text = text.replace("private Long id", "private UUID id");
        // SQL : BIGINT GENERATED BY DEFAULT AS IDENTITY → UUID DEFAULT gen_random_uuid()
        if (path.endsWith(".sql")) {
            text = text.replace(
                "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY",
                "UUID DEFAULT gen_random_uuid() PRIMARY KEY");
        }
        return text;
    }
```

- [ ] **Lancer tous les tests (doivent passer)**

```bash
mvn test
```
Attendu : tous les tests passent sans erreur.

- [ ] **Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessor.java src/test/java/com/mr486/generator/pipeline/processor/ResourceExpandProcessorTest.java
git commit -m "feat: ResourceExpandProcessor - INTEGER and UUID id types"
```

---

## Vérification finale

- [ ] **Lancer la suite complète**

```bash
mvn test
```
Attendu : `BUILD SUCCESS`, tous les tests passent.

- [ ] **Vérifier que PlatformGeneratorService n'a pas été touché depuis la Task 3**

```bash
wc -l src/main/java/com/mr486/generator/service/PlatformGeneratorService.java
```
Attendu : ≤ 30 lignes.

- [ ] **Commit final si propre**

```bash
git add -A
git commit -m "chore: pipeline refactoring complete - all processors implemented"
```
