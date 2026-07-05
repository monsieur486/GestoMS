package com.mr486.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.service.PlatformGeneratorService;
import com.mr486.generator.zip.GeneratedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Oracle de non-régression (golden-master) pour le refactor des processors.
 *
 * <p>Fige la sortie complète du pipeline — pour chaque cas, une empreinte
 * {@code path  sha256(contenu)  executable} triée par chemin — et la compare à un fichier
 * golden versionné sous {@code src/test/resources/golden/}. Toute modification du générateur
 * qui déplace un octet de la plateforme générée casse ce test : c'est voulu.
 *
 * <p><b>Exemple :</b> {@code -Dgolden.write=true} (ré)écrit les goldens depuis la sortie
 * courante au lieu de comparer ; sans cette propriété, le test échoue si l'empreinte diverge
 * du golden. On n'active {@code golden.write} que pour figer une sortie de référence assumée.
 */
@SpringBootTest
class GeneratorGoldenMasterTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");

    @Autowired
    private PlatformGeneratorService service;

    @Test
    void default_body() {
        verify("default", new PlatformGenerationRequest());
    }

    @Test
    void one_postgres_long() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(resource("order-service", "Order", DatabaseType.POSTGRES, IdType.LONG)));
        verify("one-postgres", req);
    }

    @Test
    void one_mongo() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(resource("order-service", "Order", DatabaseType.MONGO, IdType.LONG)));
        verify("one-mongo", req);
    }

    @Test
    void one_h2() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(resource("order-service", "Order", DatabaseType.H2, IdType.LONG)));
        verify("one-h2", req);
    }

    @Test
    void one_postgres_uuid() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(resource("order-service", "Order", DatabaseType.POSTGRES, IdType.UUID)));
        verify("one-uuid", req);
    }

    @Test
    void full_three_resources_all_features() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(
            resource("order-service", "Order", DatabaseType.POSTGRES, IdType.LONG),
            resource("product-service", "Product", DatabaseType.MONGO, IdType.LONG),
            resource("inventory-service", "Item", DatabaseType.H2, IdType.INTEGER)
        ));
        req.getFeatures().setSpringbootAdmin(true);
        req.getFeatures().setWebUI(true);
        req.getBatch().setGrafana(true);
        verify("full", req);
    }

    @Test
    void batch_disabled() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.getBatch().setEnabled(false);
        verify("batch-off", req);
    }

    // Construit une ressource métier avec ses variantes de base et d'identifiant.
    private ResourceModuleRequest resource(String serviceName, String className,
                                           DatabaseType db, IdType id) {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName(serviceName);
        r.setClassName(className);
        r.setDatabaseType(db);
        r.setIdType(id);
        return r;
    }

    // Compare l'empreinte courante au golden, ou l'écrit en mode -Dgolden.write=true.
    private void verify(String name, PlatformGenerationRequest req) {
        String fingerprint = fingerprint(service.generate(req));
        Path golden = GOLDEN_DIR.resolve(name + ".txt");
        try {
            if (Boolean.getBoolean("golden.write") || !Files.exists(golden)) {
                Files.createDirectories(GOLDEN_DIR);
                Files.writeString(golden, fingerprint, StandardCharsets.UTF_8);
                return;
            }
            String expected = Files.readString(golden, StandardCharsets.UTF_8);
            assertThat(fingerprint)
                .as("empreinte du cas '%s' — un écart signale une régression de sortie", name)
                .isEqualTo(expected);
        } catch (IOException e) {
            throw new IllegalStateException("accès au golden '" + name + "' impossible", e);
        }
    }

    // Empreinte déterministe : une ligne "path  sha256  executable" par fichier, triée par chemin.
    private String fingerprint(List<GeneratedFile> files) {
        return files.stream()
            .sorted((a, b) -> a.path().compareTo(b.path()))
            .map(f -> f.path() + "  " + sha256(f.content()) + "  " + f.executable())
            .collect(Collectors.joining("\n"));
    }

    // Calcule le SHA-256 d'un contenu en hexadécimal.
    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
