package com.mr486.generator.model;

import com.mr486.generator.dto.PlatformGenerationRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que {@link GenerationContext#from(PlatformGenerationRequest)} calcule
 * correctement la racine cible à partir du nom de la requête, et se replie sur
 * {@code ms-platform} lorsque le nom est absent ou vide.
 */
class GenerationContextTest {

    @Test
    void uses_request_name_as_target_root() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("my-platform");
        GenerationContext ctx = GenerationContext.from(req);
        assertThat(ctx.getTargetRoot()).isEqualTo("my-platform");
        assertThat(ctx.getSourceRoot()).isEqualTo("ms-platform");
        assertThat(ctx.getRequest()).isSameAs(req);
    }

    @Test
    void falls_back_to_ms_platform_when_name_blank() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName("  ");
        GenerationContext ctx = GenerationContext.from(req);
        assertThat(ctx.getTargetRoot()).isEqualTo("ms-platform");
    }

    @Test
    void falls_back_to_ms_platform_when_name_null() {
        PlatformGenerationRequest req = new PlatformGenerationRequest();
        req.setName(null);
        GenerationContext ctx = GenerationContext.from(req);
        assertThat(ctx.getTargetRoot()).isEqualTo("ms-platform");
    }
}
