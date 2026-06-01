package com.mr486.generator.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que {@link FeatureOptions} est correctement désérialisé depuis JSON :
 * les flags camelCase sont reconnus et les flags inconnus (anciens ou supprimés)
 * n'empêchent pas la désérialisation.
 */
class FeatureOptionsDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void maps_camel_case_flags() throws Exception {
        String json = "{\"springbootAdmin\":true,\"webUI\":true}";
        FeatureOptions f = mapper.readValue(json, FeatureOptions.class);
        assertThat(f.isSpringbootAdmin()).isTrue();
        assertThat(f.isWebUI()).isTrue();
    }

    @Test
    void ignores_legacy_unknown_flags() throws Exception {
        // Une ancienne commande utilisant les flags supprimés ne doit pas casser la désérialisation.
        String json = "{\"keycloak\":true,\"redis\":true,\"loki\":false,\"springbootAdmin\":true}";
        FeatureOptions f = mapper.readValue(json, FeatureOptions.class);
        assertThat(f.isSpringbootAdmin()).isTrue();
        assertThat(f.isWebUI()).isFalse(); // défaut
    }
}
