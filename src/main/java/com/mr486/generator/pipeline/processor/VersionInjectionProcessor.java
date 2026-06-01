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
        if (ProcessorUtils.containsNullByte(f.content())) return f;
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
        text = replaceImage(text, "postgres",                  TEMPLATE.getPostgres(), cfg.getPostgres(), "POSTGRES_VERSION");
        text = replaceImage(text, "quay.io/keycloak/keycloak", TEMPLATE.getKeycloak(), cfg.getKeycloak(), "KEYCLOAK_VERSION");
        text = replaceImage(text, "rabbitmq",                  TEMPLATE.getRabbitmq(), cfg.getRabbitmq(), "RABBITMQ_VERSION");
        text = replaceImage(text, "redis",                     TEMPLATE.getRedis(),    cfg.getRedis(),    "REDIS_VERSION");
        text = replaceImage(text, "mongo",                     TEMPLATE.getMongo(),    cfg.getMongo(),    "MONGO_VERSION");
        text = replaceImage(text, "grafana/loki",              TEMPLATE.getLoki(),     cfg.getLoki(),     "LOKI_VERSION");
        text = replaceImage(text, "grafana/promtail",          TEMPLATE.getPromtail(), cfg.getPromtail(), "PROMTAIL_VERSION");
        text = replaceImage(text, "grafana/grafana",           TEMPLATE.getGrafana(),  cfg.getGrafana(),  "GRAFANA_VERSION");
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
        String[][] replacements = {
            { "<artifactId>spring-boot-starter-parent</artifactId><version>" + TEMPLATE.getSpringBoot()      + "</version>",
              "<artifactId>spring-boot-starter-parent</artifactId><version>" + cfg.getSpringBoot()           + "</version>" },
            { "<spring-cloud.version>"                                        + TEMPLATE.getSpringCloud()     + "</spring-cloud.version>",
              "<spring-cloud.version>"                                        + cfg.getSpringCloud()          + "</spring-cloud.version>" },
            { "<mongock.version>"                                             + TEMPLATE.getMongock()         + "</mongock.version>",
              "<mongock.version>"                                             + cfg.getMongock()              + "</mongock.version>" },
            { "<artifactId>spring-boot-admin-starter-server</artifactId><version>" + TEMPLATE.getSpringBootAdmin() + "</version>",
              "<artifactId>spring-boot-admin-starter-server</artifactId><version>" + cfg.getSpringBootAdmin()      + "</version>" },
        };
        for (String[] r : replacements) text = text.replace(r[0], r[1]);
        return text;
    }
}
