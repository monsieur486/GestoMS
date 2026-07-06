package com.mr486.generator.pipeline.processor;

import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.contentOf;
import static com.mr486.generator.pipeline.processor.ProcessorTestHelper.defaultCtx;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.ctxWithResources;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.resource;
import static com.mr486.generator.pipeline.processor.ResourceExpandFixtures.serviceAFiles;
import static org.assertj.core.api.Assertions.assertThat;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.zip.GeneratedFile;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vérifie le clonage de base de {@link ResourceExpandProcessor} : nom et classe dérivés dans les
 * chemins et le contenu, préfixe de route par défaut, génération multi-services, retrait des
 * modules par défaut et non-clonage des sous-répertoires de patch. Variantes de base et d'id
 * couvertes par {@code ResourceExpand{H2,Mongo,IdType}Test}.
 */
class ResourceExpandProcessorTest {

    private final ResourceExpandProcessor processor = ResourceExpandFixtures.processor();

    @Test
    void noop_when_resources_empty() {
        GenerationContext ctx = defaultCtx();
        List<GeneratedFile> input = serviceAFiles("ms-platform");
        List<GeneratedFile> result = processor.process(input, ctx);
        assertThat(result).hasSize(input.size());
    }

    @Test
    void removes_default_services_when_resources_provided() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).noneMatch(f -> f.path().contains("/service-a/")
            && !f.path().contains("/service-a/service-batch/")
            && !f.path().contains("/service-a/service-consumer/"));
        assertThat(result).noneMatch(f -> f.path().contains("/service-b/"));
        assertThat(result).noneMatch(f -> f.path().contains("/service-c/"));
    }

    @Test
    void generates_service_with_correct_name_in_paths() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).anyMatch(f -> f.path().contains("/invoice/"));
        assertThat(result).anyMatch(f -> f.path().contains("/invoice/") && f.path().endsWith("Application.java"));
    }

    @Test
    void null_routePrefix_defaults_to_api_plural() {
        ResourceModuleRequest r = new ResourceModuleRequest();
        r.setServiceName("order-service");
        r.setClassName("Order");
        // routePrefix left null
        r.setDatabaseType(DatabaseType.POSTGRES);
        r.setIdType(IdType.LONG);
        GenerationContext ctx = ctxWithResources(List.of(r));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        result.stream()
            .filter(f -> f.path().endsWith("OrderController.java"))
            .forEach(f -> {
                assertThat(contentOf(f)).contains("/api/orders");
                assertThat(contentOf(f)).doesNotContain("/api/resources-a");
            });
    }

    @Test
    void replaces_ResourceA_with_className_in_content() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        result.stream()
            .filter(f -> f.path().contains("/invoice/") && f.path().endsWith(".java"))
            .forEach(f -> {
                assertThat(contentOf(f)).doesNotContain("ResourceA");
                assertThat(contentOf(f)).doesNotContain("service-a");
            });
    }

    @Test
    void generates_multiple_services() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG),
            resource("product", "Product", "/api/products", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        assertThat(result).anyMatch(f -> f.path().contains("/invoice/"));
        assertThat(result).anyMatch(f -> f.path().contains("/product/"));
    }

    @Test
    void does_not_clone_patch_subdirs() {
        GenerationContext ctx = ctxWithResources(List.of(
            resource("invoice", "Invoice", "/api/invoices", DatabaseType.POSTGRES, IdType.LONG)
        ));
        List<GeneratedFile> result = processor.process(serviceAFiles("ms-platform"), ctx);
        // patch service-a/service-batch/ and service-a/service-consumer/ must NOT be cloned
        assertThat(result).noneMatch(f -> f.path().startsWith("ms-platform/invoice/service-batch/"));
        assertThat(result).noneMatch(f -> f.path().startsWith("ms-platform/invoice/service-consumer/"));
    }
}
