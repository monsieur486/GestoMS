package com.mr486.generator.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Vérifie les contraintes de Bean Validation de {@link PlatformGenerationRequest}, y compris la
 * cascade {@code @Valid} vers les {@link ResourceModuleRequest} imbriqués et le bornage de la
 * liste (défense contre l'épuisement mémoire).
 */
class PlatformGenerationRequestValidationTest {

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
    private ResourceModuleRequest validResource() {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName("order-service");
        r.setClassName("Order");
        return r;
    }

    @Test
    void default_request_is_valid() {
        // Un body vide produit la plateforme de référence : les valeurs par défaut DOIVENT passer.
        assertThat(validator.validate(new PlatformGenerationRequest())).isEmpty();
    }

    @Test
    void request_with_valid_resource_is_valid() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(List.of(validResource()));
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void name_with_crlf_is_rejected() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("a\r\nSet-Cookie: x=y");
        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void invalid_java_version_is_rejected() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setJavaVersion("17; rm -rf /");
        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("javaVersion"));
    }

    @Test
    void invalid_group_id_is_rejected() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setGroupId("com.mr486; drop table");
        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("groupId"));
    }

    @Test
    void too_many_resources_is_rejected() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        List<ResourceModuleRequest> many = IntStream.range(0, 21)
            .mapToObj(i -> {
                ResourceModuleRequest r = new ResourceModuleRequest();
                r.setServiceName("svc-" + i);
                r.setClassName("Svc" + i);
                return r;
            })
            .collect(Collectors.toList());
        req.setResources(many);
        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("resources"));
    }

    @Test
    void invalid_nested_resource_cascades() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        ResourceModuleRequest bad = new ResourceModuleRequest();
        bad.setServiceName("../evil");
        bad.setClassName("Order");
        req.setResources(List.of(bad));
        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().startsWith("resources"));
    }

    @Test
    void null_resource_element_is_rejected() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setResources(new ArrayList<>(Arrays.asList((ResourceModuleRequest) null)));
        assertThat(validator.validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().startsWith("resources"));
    }
}
