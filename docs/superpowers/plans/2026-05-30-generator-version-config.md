# Centralisation des versions du générateur — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extraire toutes les versions en dur (images Docker + 2 littéraux Maven) vers `application.yml` (`platform.versions`) lu par `@ConfigurationProperties`, et injecter ces valeurs à la génération via un nouveau `VersionInjectionProcessor @Order(70)` qui rend les images du projet généré pilotables par `.env`.

**Architecture:** Nouveau bean `PlatformVersions` (`@Component @ConfigurationProperties("platform.versions")`, défauts de champ = littéraux du template). Nouveau `VersionInjectionProcessor @Order(70)` (dernier de la chaîne, après `CrossCuttingConfig @Order(60)`) : substitutions textuelles sur `docker-compose.yml` (images → `${VAR:-default}`, `build: ./x` → forme longue + `args: JAVA_IMAGE`), les 11 `Dockerfile` (`ARG`+`FROM ${JAVA_IMAGE}`), `.env`/`dist.env` (bloc versions), et 2 littéraux Maven (parent spring-boot, spring-boot-admin) + properties `spring-cloud`/`mongock`. Le template reste inchangé.

**Tech Stack:** Java 17, Spring Boot, Lombok, JUnit5 + AssertJ. Le générateur lui-même est un projet Maven mono-module.

---

## Spec
`docs/superpowers/specs/2026-05-30-generator-version-config-design.md`

## Carte des fichiers

Racine générateur : `/home/mr486/Developpement/Projets/GestoMS`.
- **Nouveaux :**
  - `src/main/resources/application.yml` — bloc `platform.versions` (source de vérité éditable).
  - `src/main/java/com/mr486/generator/config/PlatformVersions.java` — POJO `@ConfigurationProperties`.
  - `src/main/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessor.java` — processor `@Order(70)`.
  - `src/test/java/com/mr486/generator/config/PlatformVersionsTest.java` — binding YAML.
  - `src/test/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessorTest.java` — unitaires.
- **Modifiés :**
  - `src/main/java/com/mr486/generator/pipeline/FileProcessor.java` — Javadoc : ajouter l'étape `@Order(70)`.
- **Template :** aucun changement → `TemplateLoaderTest` parité **173 inchangée**.

## Conventions & pièges
- Les processors sont des beans Spring triés par `@Order`, injectés en `List<FileProcessor>` dans `PlatformGeneratorService`. Un nouveau `@Component @Order(70)` est automatiquement ajouté en fin de chaîne.
- Les tests unitaires de processor **n'utilisent pas** Spring : ils instancient le processor directement (`new VersionInjectionProcessor(new PlatformVersions())`) et utilisent les helpers `ProcessorTestHelper` (`file(path, content)`, `contentOf(file)`).
- **Source littérale vs valeur cible** : la recherche se fait sur le littéral **du template** (constante `TEMPLATE = new PlatformVersions()`, défauts de champ = littéraux), la valeur injectée dans le résultat vient de l'instance **config** (`cfg`, surchargée par le YAML). Quand config == défaut, les deux coïncident. C'est le même pattern que `BatchConfigProcessor.DEFAULTS`.
- **Images/Dockerfile/.env ne sont PAS no-op même aux défauts** : ils passent toujours en forme interpolée `${VAR:-…}` (c'est le but). Seuls les **littéraux Maven** sont no-op quand config == défaut.
- **`<java.version>` n'est PAS géré ici** : il est déjà piloté par `PackagePlaceholderProcessor @Order(30)` depuis `request.javaVersion`. Donc **pas de clé `java`** dans `platform.versions` (évite le conflit signalé dans le spec et toute clé morte).
- **Piège du jar périmé** (Task 3) : rebuild `mvn clean package` AVANT de générer ; `pkill`/lancement en commandes séparées ; sandbox désactivé.
- Lombok est déjà utilisé dans le projet (`@Value`, `@RequiredArgsConstructor`) → `@Data` disponible.

---

## Task 1 : `PlatformVersions` + `application.yml` + test de binding

**Files:**
- Create: `src/main/java/com/mr486/generator/config/PlatformVersions.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/com/mr486/generator/config/PlatformVersionsTest.java`

- [ ] **Step 1 : Créer `PlatformVersions.java`**

```java
package com.mr486.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Versions centralisées injectées dans la plateforme générée.
 * <p>
 * Bean Spring lié au bloc {@code platform.versions} d'{@code application.yml}. Les valeurs par défaut
 * des champs ci-dessous DOIVENT rester égales aux littéraux présents dans le template
 * ({@code docker-compose.yml}, {@code Dockerfile}, poms) : {@link com.mr486.generator.pipeline.processor.VersionInjectionProcessor}
 * s'en sert à la fois comme constante de recherche (une instance neuve = littéraux du template) et,
 * via l'instance injectée et surchargée par le YAML, comme valeur cible.
 */
@Component
@ConfigurationProperties("platform.versions")
@Data
public class PlatformVersions {
    /** Image de base des 11 Dockerfile + arg build {@code JAVA_IMAGE}. */
    private String javaImage = "eclipse-temurin:17-jre";
    /** Version du parent {@code spring-boot-starter-parent} (root pom). */
    private String springBoot = "3.5.5";
    /** {@code <spring-cloud.version>} du root pom. */
    private String springCloud = "2025.0.0";
    /** {@code <mongock.version>} du root pom. */
    private String mongock = "5.5.1";
    /** Version de {@code spring-boot-admin-starter-server} (ms-admin). */
    private String springBootAdmin = "3.5.5";
    private String postgres = "16";
    private String keycloak = "26.5.6";
    private String rabbitmq = "3.13-management";
    private String redis = "7-alpine";
    private String mongo = "7";
    private String loki = "3.2.1";
    private String promtail = "3.2.1";
    private String grafana = "11.2.2";
}
```

- [ ] **Step 2 : Créer `application.yml`** (le générateur n'en avait pas ; valeurs = littéraux actuels)

`src/main/resources/application.yml` :
```yaml
platform:
  versions:
    java-image: eclipse-temurin:17-jre
    spring-boot: 3.5.5
    spring-cloud: 2025.0.0
    mongock: 5.5.1
    spring-boot-admin: 3.5.5
    postgres: "16"
    keycloak: 26.5.6
    rabbitmq: 3.13-management
    redis: 7-alpine
    mongo: "7"
    loki: 3.2.1
    promtail: 3.2.1
    grafana: 11.2.2
```

- [ ] **Step 3 : Écrire le test de binding** (échoue tant que `application.yml`/POJO ne bindent pas)

`src/test/java/com/mr486/generator/config/PlatformVersionsTest.java` :
```java
package com.mr486.generator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlatformVersionsTest {

    @Autowired
    PlatformVersions versions;

    @Test
    void binds_all_versions_from_application_yaml() {
        assertThat(versions.getJavaImage()).isEqualTo("eclipse-temurin:17-jre");
        assertThat(versions.getSpringBoot()).isEqualTo("3.5.5");
        assertThat(versions.getSpringCloud()).isEqualTo("2025.0.0");
        assertThat(versions.getMongock()).isEqualTo("5.5.1");
        assertThat(versions.getSpringBootAdmin()).isEqualTo("3.5.5");
        assertThat(versions.getPostgres()).isEqualTo("16");
        assertThat(versions.getKeycloak()).isEqualTo("26.5.6");
        assertThat(versions.getRabbitmq()).isEqualTo("3.13-management");
        assertThat(versions.getRedis()).isEqualTo("7-alpine");
        assertThat(versions.getMongo()).isEqualTo("7");
        assertThat(versions.getLoki()).isEqualTo("3.2.1");
        assertThat(versions.getPromtail()).isEqualTo("3.2.1");
        assertThat(versions.getGrafana()).isEqualTo("11.2.2");
    }

    @Test
    void code_defaults_equal_template_literals() {
        // Une instance neuve = littéraux du template (constante de recherche du processor).
        assertThat(new PlatformVersions().getPostgres()).isEqualTo("16");
        assertThat(new PlatformVersions().getJavaImage()).isEqualTo("eclipse-temurin:17-jre");
    }
}
```

- [ ] **Step 4 : Lancer le test — il doit passer** (binding + défauts)

Run: `mvn -q test -Dtest=PlatformVersionsTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert. Si maven échoue pour sandbox/réseau, le signaler.

- [ ] **Step 5 : Suite complète verte (parité 173 inchangée)**

Run: `mvn -q test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` toujours 173 — aucun fichier template ajouté).

- [ ] **Step 6 : Commit**

```bash
git add src/main/java/com/mr486/generator/config/PlatformVersions.java \
        src/main/resources/application.yml \
        src/test/java/com/mr486/generator/config/PlatformVersionsTest.java
git commit -m "feat(generator): PlatformVersions config (platform.versions) + application.yml"
```
Terminer le corps du message par :
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 2 : `VersionInjectionProcessor @Order(70)` + tests unitaires + Javadoc

**Files:**
- Create: `src/main/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessor.java`
- Create: `src/test/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessorTest.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/FileProcessor.java`

- [ ] **Step 1 : Écrire les tests unitaires** (échouent : classe absente)

`src/test/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessorTest.java` :
```java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.config.PlatformVersions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

class VersionInjectionProcessorTest {

    private final VersionInjectionProcessor processor =
        new VersionInjectionProcessor(new PlatformVersions());   // défauts = littéraux template

    private final GenerationContext ctx = defaultCtx();

    private String run(String path, String content) {
        List<GeneratedFile> out = processor.process(List.of(file(path, content)), ctx);
        return contentOf(out.get(0));
    }

    @Test
    void compose_image_becomes_env_interpolation() {
        String out = run("ms-platform/docker-compose.yml",
            "  keycloak-db:\n    image: postgres:16\n");
        assertThat(out).contains("image: postgres:${POSTGRES_VERSION:-16}");
        assertThat(out).doesNotContain("image: postgres:16\n");
    }

    @Test
    void compose_all_managed_images_interpolated() {
        String src = "    image: quay.io/keycloak/keycloak:26.5.6\n"
                   + "    image: rabbitmq:3.13-management\n"
                   + "    image: redis:7-alpine\n"
                   + "    image: mongo:7\n"
                   + "    image: grafana/loki:3.2.1\n"
                   + "    image: grafana/promtail:3.2.1\n"
                   + "    image: grafana/grafana:11.2.2\n";
        String out = run("ms-platform/docker-compose.yml", src);
        assertThat(out).contains("quay.io/keycloak/keycloak:${KEYCLOAK_VERSION:-26.5.6}");
        assertThat(out).contains("rabbitmq:${RABBITMQ_VERSION:-3.13-management}");
        assertThat(out).contains("redis:${REDIS_VERSION:-7-alpine}");
        assertThat(out).contains("mongo:${MONGO_VERSION:-7}");
        assertThat(out).contains("grafana/loki:${LOKI_VERSION:-3.2.1}");
        assertThat(out).contains("grafana/promtail:${PROMTAIL_VERSION:-3.2.1}");
        assertThat(out).contains("grafana/grafana:${GRAFANA_VERSION:-11.2.2}");
    }

    @Test
    void compose_build_short_form_expands_with_java_image_arg() {
        String out = run("ms-platform/docker-compose.yml",
            "  ms-eureka:\n    build: ./ms-eureka\n    env_file: [.env]\n");
        assertThat(out).contains(
            "    build:\n"
          + "      context: ./ms-eureka\n"
          + "      args:\n"
          + "        JAVA_IMAGE: ${JAVA_IMAGE:-eclipse-temurin:17-jre}");
        assertThat(out).doesNotContain("build: ./ms-eureka\n");
        assertThat(out).contains("    env_file: [.env]");   // ligne suivante préservée
    }

    @Test
    void dockerfile_uses_arg_java_image() {
        String out = run("ms-platform/ms-eureka/Dockerfile",
            "FROM eclipse-temurin:17-jre\nWORKDIR /app\n");
        assertThat(out).contains("ARG JAVA_IMAGE=eclipse-temurin:17-jre\nFROM ${JAVA_IMAGE}");
        assertThat(out).doesNotContain("FROM eclipse-temurin:17-jre\n");
    }

    @Test
    void env_file_gets_version_block() {
        String out = run("ms-platform/.env", "REDIS_HOST=redis\n");
        assertThat(out).contains("# --- image versions ---");
        assertThat(out).contains("JAVA_IMAGE=eclipse-temurin:17-jre");
        assertThat(out).contains("POSTGRES_VERSION=16");
        assertThat(out).contains("GRAFANA_VERSION=11.2.2");
        assertThat(out).contains("REDIS_HOST=redis");   // contenu d'origine préservé
    }

    @Test
    void dist_env_also_gets_version_block() {
        String out = run("ms-platform/dist.env", "REDIS_HOST=redis\n");
        assertThat(out).contains("# --- image versions ---");
        assertThat(out).contains("KEYCLOAK_VERSION=26.5.6");
    }

    @Test
    void root_pom_parent_and_properties_are_noop_at_defaults() {
        String src = "<parent><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-starter-parent</artifactId><version>3.5.5</version><relativePath/></parent>"
            + "<properties><java.version>17</java.version>"
            + "<spring-cloud.version>2025.0.0</spring-cloud.version>"
            + "<mongock.version>5.5.1</mongock.version></properties>";
        String out = run("ms-platform/pom.xml", src);
        assertThat(out).isEqualTo(src);   // config == défauts → pas de changement
    }

    @Test
    void root_pom_properties_rewritten_when_config_overridden() {
        PlatformVersions cfg = new PlatformVersions();
        cfg.setSpringBoot("3.6.0");
        cfg.setSpringCloud("2025.1.0");
        cfg.setMongock("5.6.0");
        VersionInjectionProcessor p = new VersionInjectionProcessor(cfg);
        String src = "<parent><groupId>org.springframework.boot</groupId>"
            + "<artifactId>spring-boot-starter-parent</artifactId><version>3.5.5</version><relativePath/></parent>"
            + "<spring-cloud.version>2025.0.0</spring-cloud.version>"
            + "<mongock.version>5.5.1</mongock.version>";
        String out = contentOf(p.process(List.of(file("ms-platform/pom.xml", src)), defaultCtx()).get(0));
        assertThat(out).contains("<artifactId>spring-boot-starter-parent</artifactId><version>3.6.0</version>");
        assertThat(out).contains("<spring-cloud.version>2025.1.0</spring-cloud.version>");
        assertThat(out).contains("<mongock.version>5.6.0</mongock.version>");
    }

    @Test
    void ms_admin_pom_spring_boot_admin_rewritten_when_overridden() {
        PlatformVersions cfg = new PlatformVersions();
        cfg.setSpringBootAdmin("3.6.0");
        VersionInjectionProcessor p = new VersionInjectionProcessor(cfg);
        String src = "<dependency><groupId>de.codecentric</groupId>"
            + "<artifactId>spring-boot-admin-starter-server</artifactId><version>3.5.5</version></dependency>";
        String out = contentOf(p.process(List.of(file("ms-platform/ms-admin/pom.xml", src)), defaultCtx()).get(0));
        assertThat(out).contains("<artifactId>spring-boot-admin-starter-server</artifactId><version>3.6.0</version>");
    }

    @Test
    void java_version_property_is_left_untouched() {
        // Géré par PackagePlaceholderProcessor @Order(30), pas ici.
        String out = run("ms-platform/pom.xml", "<properties><java.version>21</java.version></properties>");
        assertThat(out).contains("<java.version>21</java.version>");
    }

    @Test
    void compose_image_rewrite_is_idempotent() {
        String once = run("ms-platform/docker-compose.yml", "    image: postgres:16\n");
        String twice = run("ms-platform/docker-compose.yml", once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void binary_file_is_passed_through() {
        byte[] bin = new byte[]{1, 0, 2, 0};
        List<GeneratedFile> out = processor.process(
            List.of(new GeneratedFile("ms-platform/x.bin", bin, false)), ctx);
        assertThat(out.get(0).content()).isEqualTo(bin);
    }

    @Test
    void unrelated_file_unchanged() {
        String out = run("ms-platform/README.md", "# hello\n");
        assertThat(out).isEqualTo("# hello\n");
    }
}
```

- [ ] **Step 2 : Lancer les tests — ils doivent échouer** (compilation : classe absente)

Run: `mvn -q test -Dtest=VersionInjectionProcessorTest 2>&1 | grep -E 'ERROR|BUILD|cannot find symbol'`
Expected: échec de compilation (`VersionInjectionProcessor` introuvable).

- [ ] **Step 3 : Écrire `VersionInjectionProcessor.java`**

```java
package com.mr486.generator.pipeline.processor;

import com.mr486.generator.config.PlatformVersions;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalise toutes les versions de la plateforme générée depuis {@link PlatformVersions}.
 * <p>
 * Tourne en dernier ({@code @Order(70)}, après {@link CrossCuttingConfigProcessor}) pour voir
 * l'ensemble final des fichiers — y compris les blocs {@code image:}/{@code build:} ajoutés
 * dynamiquement par resource. Transformations :
 * <ul>
 *   <li>{@code docker-compose.yml} : {@code image: repo:tag} → {@code image: repo:${VAR:-tag}} ;
 *       {@code build: ./svc} → forme longue + {@code args: JAVA_IMAGE} ;</li>
 *   <li>{@code Dockerfile} : {@code FROM <javaImage>} → {@code ARG JAVA_IMAGE=…} + {@code FROM ${JAVA_IMAGE}} ;</li>
 *   <li>{@code .env}/{@code dist.env} : ajout d'un bloc de versions d'images ;</li>
 *   <li>poms : parent {@code spring-boot}, {@code spring-cloud}/{@code mongock} properties, {@code spring-boot-admin}.</li>
 * </ul>
 * Le {@code <java.version>} N'est PAS géré ici (déjà piloté par {@link PackagePlaceholderProcessor}).
 * La constante {@link #TEMPLATE} (instance neuve) fournit les littéraux de recherche du template ;
 * l'instance injectée {@code cfg} fournit les valeurs cibles (surchargées par {@code application.yml}).
 */
@Component
@Order(70)
public class VersionInjectionProcessor implements FileProcessor {

    private static final PlatformVersions TEMPLATE = new PlatformVersions();
    private static final Pattern BUILD = Pattern.compile("(?m)^( *)build: \\./(\\S+)$");

    private final PlatformVersions cfg;

    public VersionInjectionProcessor(PlatformVersions cfg) {
        this.cfg = cfg;
    }

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        return files.stream().map(this::transform).toList();
    }

    private GeneratedFile transform(GeneratedFile f) {
        if (containsNullByte(f.content())) return f;
        String path = f.path();
        String text = new String(f.content(), StandardCharsets.UTF_8);
        String out = text;
        if (path.endsWith("docker-compose.yml")) {
            out = rewriteComposeImages(out);
            out = rewriteComposeBuilds(out);
        } else if (path.endsWith("Dockerfile")) {
            out = rewriteDockerfile(out);
        } else if (path.endsWith("/.env") || path.endsWith("/dist.env")) {
            out = appendEnvVersions(out);
        } else if (path.endsWith("pom.xml")) {
            out = rewritePom(out);
        }
        if (out.equals(text)) return f;
        return new GeneratedFile(path, out.getBytes(StandardCharsets.UTF_8), f.executable());
    }

    private String rewriteComposeImages(String text) {
        text = replaceImage(text, "postgres",                       TEMPLATE.getPostgres(), cfg.getPostgres(), "POSTGRES_VERSION");
        text = replaceImage(text, "quay.io/keycloak/keycloak",      TEMPLATE.getKeycloak(), cfg.getKeycloak(), "KEYCLOAK_VERSION");
        text = replaceImage(text, "rabbitmq",                       TEMPLATE.getRabbitmq(), cfg.getRabbitmq(), "RABBITMQ_VERSION");
        text = replaceImage(text, "redis",                          TEMPLATE.getRedis(),    cfg.getRedis(),    "REDIS_VERSION");
        text = replaceImage(text, "mongo",                          TEMPLATE.getMongo(),    cfg.getMongo(),    "MONGO_VERSION");
        text = replaceImage(text, "grafana/loki",                   TEMPLATE.getLoki(),     cfg.getLoki(),     "LOKI_VERSION");
        text = replaceImage(text, "grafana/promtail",               TEMPLATE.getPromtail(), cfg.getPromtail(), "PROMTAIL_VERSION");
        text = replaceImage(text, "grafana/grafana",                TEMPLATE.getGrafana(),  cfg.getGrafana(),  "GRAFANA_VERSION");
        return text;
    }

    private String replaceImage(String text, String repo, String templateTag, String cfgTag, String var) {
        return text.replace(
            "image: " + repo + ":" + templateTag,
            "image: " + repo + ":${" + var + ":-" + cfgTag + "}");
    }

    private String rewriteComposeBuilds(String text) {
        Matcher m = BUILD.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String indent = m.group(1);
            String name = m.group(2);
            String repl = indent + "build:\n"
                + indent + "  context: ./" + name + "\n"
                + indent + "  args:\n"
                + indent + "    JAVA_IMAGE: ${JAVA_IMAGE:-" + cfg.getJavaImage() + "}";
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String rewriteDockerfile(String text) {
        return text.replace(
            "FROM " + TEMPLATE.getJavaImage(),
            "ARG JAVA_IMAGE=" + cfg.getJavaImage() + "\nFROM ${JAVA_IMAGE}");
    }

    private String appendEnvVersions(String text) {
        if (text.contains("# --- image versions ---")) return text;
        StringBuilder b = new StringBuilder(text);
        if (!text.endsWith("\n")) b.append("\n");
        b.append("\n# --- image versions ---\n");
        b.append("JAVA_IMAGE=").append(cfg.getJavaImage()).append("\n");
        b.append("POSTGRES_VERSION=").append(cfg.getPostgres()).append("\n");
        b.append("KEYCLOAK_VERSION=").append(cfg.getKeycloak()).append("\n");
        b.append("RABBITMQ_VERSION=").append(cfg.getRabbitmq()).append("\n");
        b.append("REDIS_VERSION=").append(cfg.getRedis()).append("\n");
        b.append("MONGO_VERSION=").append(cfg.getMongo()).append("\n");
        b.append("LOKI_VERSION=").append(cfg.getLoki()).append("\n");
        b.append("PROMTAIL_VERSION=").append(cfg.getPromtail()).append("\n");
        b.append("GRAFANA_VERSION=").append(cfg.getGrafana()).append("\n");
        return b.toString();
    }

    private String rewritePom(String text) {
        text = text.replace(
            "<artifactId>spring-boot-starter-parent</artifactId><version>" + TEMPLATE.getSpringBoot() + "</version>",
            "<artifactId>spring-boot-starter-parent</artifactId><version>" + cfg.getSpringBoot() + "</version>");
        text = text.replace(
            "<spring-cloud.version>" + TEMPLATE.getSpringCloud() + "</spring-cloud.version>",
            "<spring-cloud.version>" + cfg.getSpringCloud() + "</spring-cloud.version>");
        text = text.replace(
            "<mongock.version>" + TEMPLATE.getMongock() + "</mongock.version>",
            "<mongock.version>" + cfg.getMongock() + "</mongock.version>");
        text = text.replace(
            "<artifactId>spring-boot-admin-starter-server</artifactId><version>" + TEMPLATE.getSpringBootAdmin() + "</version>",
            "<artifactId>spring-boot-admin-starter-server</artifactId><version>" + cfg.getSpringBootAdmin() + "</version>");
        return text;
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
```

- [ ] **Step 4 : Lancer les tests du processor — ils doivent passer**

Run: `mvn -q test -Dtest=VersionInjectionProcessorTest 2>&1 | grep -E 'Tests run|BUILD'`
Expected: vert (14 tests). Si échec, corriger le processor (pas les tests).

- [ ] **Step 5 : Mettre à jour le Javadoc de `FileProcessor.java`**

Dans la liste ordonnée des processors (Javadoc de l'interface), ajouter après la ligne `@Order(60)` :
```java
 *   <li>{@code @Order(70)} {@link com.mr486.generator.pipeline.processor.VersionInjectionProcessor} — injecte les versions centralisées (images Docker, .env, poms).</li>
```

- [ ] **Step 6 : Suite complète verte (parité 173 inchangée)**

Run: `mvn -q test 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (TemplateLoaderTest toujours 173).

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessor.java \
        src/test/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessorTest.java \
        src/main/java/com/mr486/generator/pipeline/FileProcessor.java
git commit -m "feat(generator): VersionInjectionProcessor @Order(70) — central versions into compose/Dockerfile/.env/poms"
```
Terminer le corps par :
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 3 : Build + génération end-to-end

**Files:** aucun (vérification). **Rebuild du jar AVANT de générer** (piège du jar périmé) ; `pkill`/lancement en commandes séparées ; sandbox désactivé.

- [ ] **Step 1 : Build complet (reconstruit le jar)**

Run: `mvn clean package 2>&1 | grep -E 'Tests run: [0-9]+, Fail|BUILD (SUCCESS|FAILURE)'`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0` (dont `TemplateLoaderTest` 173, `VersionInjectionProcessorTest`, `PlatformVersionsTest`).

- [ ] **Step 2 : Tuer un éventuel générateur (commande séparée)**

Run (sandbox désactivé) : `pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null; sleep 2; pgrep -af '[g]enerator-v5' || echo clean`
Expected: `clean`.

- [ ] **Step 3 : Lancer le générateur (commande séparée, arrière-plan, sandbox désactivé, SANS pkill)**

```bash
java -jar target/springboot-platform-generator-v5-0.0.1-SNAPSHOT.jar --server.port=8077 > /tmp/genapp.log 2>&1
```
Puis (commande séparée) :
```bash
for i in $(seq 1 60); do grep -q "Tomcat started on port 8077" /tmp/genapp.log 2>/dev/null && break; sleep 1; done
grep -q "Tomcat started on port 8077" /tmp/genapp.log && echo STARTED
```
Expected: `STARTED`.

- [ ] **Step 4 : Générer + vérifier le contenu**

```bash
curl -sS -X POST "http://localhost:8077/api/generate/platform" -H "Content-Type: application/json" \
  -d '{"name":"ms-platform","groupId":"com.acme","basePackage":"com.acme.shop","javaVersion":"17","resources":[{"serviceName":"order-service","className":"Order","routePrefix":"/api/orders","databaseType":"POSTGRES","idType":"LONG"}],"batch":{"enabled":true,"grafana":false},"features":{"springbootAdmin":true,"clientWebUI":false}}' \
  -o /tmp/refv.zip -w 'HTTP=%{http_code}\n'
rm -rf /tmp/refvx && mkdir -p /tmp/refvx && unzip -q /tmp/refv.zip -d /tmp/refvx && echo UNZIPPED
CMP=/tmp/refvx/ms-platform/docker-compose.yml
echo -n "postgres interpolé="; grep -c 'image: postgres:${POSTGRES_VERSION:-16}' "$CMP"
echo -n "keycloak interpolé="; grep -c 'keycloak:${KEYCLOAK_VERSION:-26.5.6}' "$CMP"
echo -n "build args JAVA_IMAGE="; grep -c 'JAVA_IMAGE: ${JAVA_IMAGE:-eclipse-temurin:17-jre}' "$CMP"
echo -n "plus de build: ./ court="; grep -c 'build: \./' "$CMP"
echo -n "Dockerfile ARG (eureka)="; grep -c 'FROM ${JAVA_IMAGE}' /tmp/refvx/ms-platform/ms-eureka/Dockerfile
echo -n ".env bloc versions="; grep -c '# --- image versions ---' /tmp/refvx/ms-platform/.env
echo -n "dist.env bloc versions="; grep -c '# --- image versions ---' /tmp/refvx/ms-platform/dist.env
echo -n "ms-admin SBA inchangé(3.5.5)="; grep -c '<artifactId>spring-boot-admin-starter-server</artifactId><version>3.5.5</version>' /tmp/refvx/ms-platform/ms-admin/pom.xml
echo -n "java.version=17 préservé="; grep -c '<java.version>17</java.version>' /tmp/refvx/ms-platform/pom.xml
```
Expected : `HTTP=200`, `UNZIPPED` ; `postgres interpolé`≥1, `keycloak interpolé`=1, `build args JAVA_IMAGE`≥10, **`plus de build: ./ court`=0**, `Dockerfile ARG`=1, `.env bloc`=1, `dist.env bloc`=1, `ms-admin SBA`=1 (défaut inchangé), `java.version=17`=1.

- [ ] **Step 5 : Le projet généré compile + compose valide**

```bash
cd /tmp/refvx/ms-platform && mvn -q -pl ms-eureka -am package 2>&1 | grep -E 'BUILD (SUCCESS|FAILURE)|ERROR' | head
echo "--- compose config SANS .env ---"
mv .env .env.bak; docker compose config >/dev/null 2>/tmp/cc1.err && echo CONFIG_OK_NO_ENV || cat /tmp/cc1.err
mv .env.bak .env
echo "--- compose config AVEC .env ---"
docker compose config >/dev/null 2>/tmp/cc2.err && echo CONFIG_OK_WITH_ENV || cat /tmp/cc2.err
```
Expected : `BUILD SUCCESS` ; `CONFIG_OK_NO_ENV` (les `${VAR:-default}` rendent `.env` optionnel) ; `CONFIG_OK_WITH_ENV`. Si Docker/Maven indisponible : noter explicitement comme NON vérifié.

- [ ] **Step 6 : Arrêt + arbre propre**

```bash
pkill -9 -f '[g]enerator-v5-0.0.1' 2>/dev/null
cd /home/mr486/Developpement/Projets/GestoMS && git status --short
```
Expected : arbre git propre (tout commité aux Tasks 1–2).

---

## Recovery
- `git log --oneline -4` — commits passés (PlatformVersions ; VersionInjectionProcessor).
- `ls src/main/resources/application.yml` + `grep -c 'platform.versions' src/main/resources/application.yml` → existe si Task 1 faite.
- `grep -c 'class VersionInjectionProcessor' src/main/java/com/mr486/generator/pipeline/processor/VersionInjectionProcessor.java` → `1` si Task 2 faite.
- `mvn test` SUCCESS → générateur vert (parité 173) ; oracle end-to-end = projet généré qui compile + `docker compose config` valide (Task 3).
- Reprendre à la première Step dont l'Expected échoue.

## Hors périmètre (ne pas traiter)
- Override des versions par requête API.
- Variabiliser le repo Keycloak (`quay.io/keycloak/keycloak`) — seul le tag l'est.
- Gérer `<java.version>` ici (reste piloté par `PackagePlaceholderProcessor @Order(30)`).
- Toucher au template (littéraux conservés ; `TemplateLoaderTest` 173 inchangé).
