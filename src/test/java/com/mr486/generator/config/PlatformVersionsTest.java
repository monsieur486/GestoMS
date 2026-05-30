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
        assertThat(new PlatformVersions().getPostgres()).isEqualTo("16");
        assertThat(new PlatformVersions().getJavaImage()).isEqualTo("eclipse-temurin:17-jre");
    }
}
