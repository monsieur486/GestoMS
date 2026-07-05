package com.mr486.generator.pipeline.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires de {@link YamlBlocks} : le balayeur générique reproduit le comportement des
 * retraits de bloc historiques (route de passerelle indentée à 12, service compose indenté à 2),
 * y compris les cas frontière (bloc absent, bloc en fin de texte, préservation des lignes suivantes).
 */
class YamlBlocksTest {

    @Test
    void removes_compose_service_block_up_to_next_sibling() {
        String text = String.join("\n",
            "services:",
            "  redis:",
            "    image: redis:7",
            "    ports:",
            "      - 6379",
            "  gateway:",
            "    image: gw");

        String out = YamlBlocks.removeBlock(text,
            l -> "  redis:".equals(l),
            l -> !l.startsWith("   "));

        assertThat(out).isEqualTo(String.join("\n",
            "services:",
            "  gateway:",
            "    image: gw"));
    }

    @Test
    void removes_gateway_route_block_up_to_sibling_id() {
        String text = String.join("\n",
            "            - id: order-service",
            "              uri: lb://order-service",
            "              predicates:",
            "                - Path=/order-service/**",
            "            - id: eureka",
            "              uri: lb://eureka");

        String out = YamlBlocks.removeBlock(text,
            l -> "            - id: order-service".equals(l),
            l -> l.startsWith("            - id:") || !l.startsWith("              "));

        assertThat(out).isEqualTo(String.join("\n",
            "            - id: eureka",
            "              uri: lb://eureka"));
    }

    @Test
    void returns_text_unchanged_when_start_absent() {
        String text = "services:\n  gateway:\n    image: gw";

        String out = YamlBlocks.removeBlock(text,
            l -> "  redis:".equals(l),
            l -> !l.startsWith("   "));

        assertThat(out).isEqualTo(text);
    }

    @Test
    void removes_block_at_end_of_text() {
        String text = String.join("\n",
            "services:",
            "  gateway:",
            "    image: gw",
            "  redis:",
            "    image: redis:7");

        String out = YamlBlocks.removeBlock(text,
            l -> "  redis:".equals(l),
            l -> !l.startsWith("   "));

        // Bloc en fin de texte : le \n qui précédait le bloc retiré subsiste (comportement
        // historique de removeServiceBlock, verrouillé par le golden-master à l'usage réel).
        assertThat(out).isEqualTo(String.join("\n",
            "services:",
            "  gateway:",
            "    image: gw") + "\n");
    }

    @Test
    void skips_blank_lines_inside_the_block() {
        String text = String.join("\n",
            "  redis:",
            "    image: redis:7",
            "",
            "    ports:",
            "      - 6379",
            "  gateway:",
            "    image: gw");

        String out = YamlBlocks.removeBlock(text,
            l -> "  redis:".equals(l),
            l -> !l.startsWith("   "));

        assertThat(out).isEqualTo(String.join("\n",
            "  gateway:",
            "    image: gw"));
    }
}
