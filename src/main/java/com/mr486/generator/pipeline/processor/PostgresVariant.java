package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.ResourceModuleRequest;
import org.springframework.stereotype.Component;

/**
 * Stratégie de base PostgreSQL : type par défaut du modèle, aucune transformation nécessaire.
 */
@Component
public class PostgresVariant implements DbVariant {

    @Override
    public DatabaseType type() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public byte[] apply(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        return content;
    }
}
