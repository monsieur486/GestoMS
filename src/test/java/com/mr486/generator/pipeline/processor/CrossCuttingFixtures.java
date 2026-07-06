package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.file;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;

/**
 * Fixtures partagées des tests des rewriters transversaux : le processor câblé avec ses huit
 * rewriters, le jeu de fichiers modèle ({@code pom.xml}, {@code docker-compose.yml}, gateway
 * {@code application.yml}) et les fabriques de ressources/contexte.
 *
 * <p>Extraites d'une classe de test unique devenue trop grosse ; consommées par
 * {@code RootPomRewriterTest}, {@code ComposeRewriterTest}, {@code GatewayRewriterTest} et,
 * pour {@code res}/{@code ctxWithResources}, par tous les tests de rewriter.
 */
final class CrossCuttingFixtures {

    private CrossCuttingFixtures() {
    }

    static final String SAMPLE_POM =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<project>\n" +
        "  <modules>\n" +
        "    <module>common-lib</module>\n" +
        "    <module>ms-eureka</module>\n" +
        "    <module>ms-gateway</module>\n" +
        "    <module>ms-admin</module>\n" +
        "    <module>service-a</module>\n" +
        "    <module>service-b</module>\n" +
        "    <module>service-c</module>\n" +
        "    <module>service-consumer</module>\n" +
        "    <module>service-batch</module>\n" +
        "    <module>ms-auth</module>\n" +
        "  </modules>\n" +
        "</project>\n";

    static final String SAMPLE_COMPOSE =
        "services:\n" +
        "  keycloak-db:\n" +
        "    image: postgres:16\n" +
        "\n" +
        "  keycloak:\n" +
        "    image: quay.io/keycloak/keycloak:26.5.6\n" +
        "\n" +
        "  rabbitmq:\n" +
        "    image: rabbitmq:3.13\n" +
        "\n" +
        "  redis:\n" +
        "    image: redis:7-alpine\n" +
        "\n" +
        "  ms-eureka:\n" +
        "    build: ./ms-eureka\n" +
        "\n" +
        "  ms-gateway:\n" +
        "    build: ./ms-gateway\n" +
        "\n" +
        "  ms-admin:\n" +
        "    build: ./ms-admin\n" +
        "\n" +
        "  ms-webui:\n" +
        "    build: ./ms-webui\n" +
        "\n" +
        "  service-a-db:\n" +
        "    image: postgres:16\n" +
        "\n" +
        "  service-b-db:\n" +
        "    image: mongo:7\n" +
        "\n" +
        "  service-a:\n" +
        "    build: ./service-a\n" +
        "\n" +
        "  service-b:\n" +
        "    build: ./service-b\n" +
        "\n" +
        "  service-c:\n" +
        "    build: ./service-c\n" +
        "\n" +
        "  service-consumer:\n" +
        "    build: ./service-consumer\n" +
        "    depends_on: [ms-eureka, keycloak, rabbitmq, redis]\n" +
        "\n" +
        "  service-batch:\n" +
        "    build: ./service-batch\n" +
        "\n" +
        "  ms-auth:\n" +
        "    build: ./ms-auth\n" +
        "\n" +
        "  admin-application:\n" +
        "    build: ./admin-application\n" +
        "\n" +
        "volumes:\n" +
        "  keycloak_db_data:\n" +
        "  redis_data:\n" +
        "  service_a_db_data:\n" +
        "  service_b_db_data:\n";

    static final String SAMPLE_GATEWAY_YML =
        "server:\n" +
        "  port: ${GATEWAY_PORT:9000}\n" +
        "spring:\n" +
        "  cloud:\n" +
        "    gateway:\n" +
        "      server:\n" +
        "        webflux:\n" +
        "          routes:\n" +
        "            - id: ms-auth\n" +
        "              uri: lb://ms-auth\n" +
        "              predicates:\n" +
        "                - Path=/auth/**\n" +
        "            - id: service-a\n" +
        "              uri: lb://service-a\n" +
        "              predicates:\n" +
        "                - Path=/service-a/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "            - id: service-b\n" +
        "              uri: lb://service-b\n" +
        "              predicates:\n" +
        "                - Path=/service-b/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "            - id: service-c\n" +
        "              uri: lb://service-c\n" +
        "              predicates:\n" +
        "                - Path=/service-c/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "            - id: service-consumer\n" +
        "              uri: lb://service-consumer\n" +
        "              predicates:\n" +
        "                - Path=/service-consumer/**\n" +
        "              filters:\n" +
        "                - StripPrefix=1\n" +
        "eureka:\n" +
        "  client:\n" +
        "    service-url:\n" +
        "      defaultZone: http://localhost:8761/eureka/\n";

    // Processor câblé avec ses huit rewriters, comme le fait Spring.
    static CrossCuttingConfigProcessor processor() {
        return new CrossCuttingConfigProcessor(List.of(
            new RootPomRewriter(), new ComposeRewriter(), new GatewayRewriter(), new RealmRewriter(),
            new TestAllRewriter(), new ReadmeRewriter(), new AggregateRewriter(), new WebUiCatalogRewriter()));
    }

    // Jeu de fichiers modèle : pom racine, docker-compose, gateway application.yml, pom de service.
    static List<GeneratedFile> sampleFiles() {
        return List.of(
            file("ms-platform/pom.xml", SAMPLE_POM),
            file("ms-platform/docker-compose.yml", SAMPLE_COMPOSE),
            file("ms-platform/ms-gateway/src/main/resources/application.yml", SAMPLE_GATEWAY_YML),
            file("ms-platform/service-a/pom.xml", "<project/>"));
    }

    // Fabrique une ressource métier (nom, classe, type de base).
    static ResourceModuleRequest res(String serviceName, String className, DatabaseType db) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(serviceName);
        r.setClassName(className);
        r.setDatabaseType(db);
        return r;
    }

    // Contexte de génération portant les ressources demandées.
    static GenerationContext ctxWithResources(ResourceModuleRequest... rs) {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(rs));
        return GenerationContext.from(req);
    }
}
