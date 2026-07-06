package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.ResourceModuleRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Stratégie de base H2 (en mémoire) : bascule le pom sur la dépendance H2, configure le datasource
 * en mémoire (mode PostgreSQL) + la console, et retire la clause {@code ON CONFLICT ... DO NOTHING}
 * des seeds SQL (non supportée par H2).
 */
@Component
public class H2Variant implements DbVariant {

    @Override
    public DatabaseType type() {
        return DatabaseType.H2;
    }

    @Override
    public byte[] apply(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        String dbName    = res.getServiceName().replace("-", "") + "db";
        String snake     = res.getServiceName().replace("-", "_");
        String snakeUp   = snake.toUpperCase(Locale.ROOT);

        if (path.endsWith("pom.xml")) {
            text = text.replace(
                "<groupId>org.postgresql</groupId><artifactId>postgresql</artifactId>",
                "<groupId>com.h2database</groupId><artifactId>h2</artifactId>"
            );
        }
        if (path.endsWith("application.yml")) {
            text = text.replace(
                "url: ${" + snakeUp + "_DATASOURCE_URL:jdbc:postgresql://localhost:5432/" + snake + "_db}",
                "url: jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            );
            text = text.replace(
                "username: ${" + snakeUp + "_DB_USERNAME:" + snake + "}",
                "username: sa"
            );
            text = text.replace(
                "password: ${" + snakeUp + "_DB_PASSWORD:" + snake + "}",
                "password:"
            );
            text = text.replace("driver-class-name: org.postgresql.Driver", "driver-class-name: org.h2.Driver");
            if (!text.contains("h2:") && text.contains("driver-class-name: org.h2.Driver")) {
                text = text.replace("driver-class-name: org.h2.Driver",
                    "driver-class-name: org.h2.Driver\n  h2:\n    console:\n      enabled: true");
            }
        }
        if (path.contains("/db/changelog/") && path.endsWith(".sql")) {
            // H2 (even with MODE=PostgreSQL) does not support ON CONFLICT — Liquibase changeset
            // history already prevents duplicate runs, so the clause is safe to drop.
            text = text.replaceAll(" ON CONFLICT\\([^)]+\\) DO NOTHING", "");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
