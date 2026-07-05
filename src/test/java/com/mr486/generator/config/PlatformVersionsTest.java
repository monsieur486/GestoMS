package com.mr486.generator.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vérifie que {@link PlatformVersions} est correctement lié aux propriétés
 * {@code platform.versions.*} de l'application, que les valeurs par défaut
 * correspondent aux littéraux du template, et que le binding YAML peut être
 * surchargé via des propriétés Spring.
 */
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
        assertThat(new PlatformVersions().getPostgres()).isEqualTo("16");
        assertThat(new PlatformVersions().getJavaImage()).isEqualTo("eclipse-temurin:17-jre");
    }

    @org.junit.jupiter.api.Nested
    @org.springframework.boot.test.context.SpringBootTest(
        properties = {"platform.versions.postgres=99", "platform.versions.java-image=custom:tag"})
    class OverrideBindingTest {

        @org.springframework.beans.factory.annotation.Autowired
        PlatformVersions versions;

        @org.junit.jupiter.api.Test
        void overridden_values_come_from_binding_not_field_defaults() {
            // Binding cassé -> retour aux défauts ("16", "eclipse-temurin:17-jre").
            org.assertj.core.api.Assertions.assertThat(versions.getPostgres()).isEqualTo("99");
            org.assertj.core.api.Assertions.assertThat(versions.getJavaImage()).isEqualTo("custom:tag");
        }
    }
}
