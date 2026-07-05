package com.mr486.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou de mise en page sur le code source du générateur lui-même
 * ({@code src/main/java} et {@code src/test/java}).
 *
 * <p>Vérifie la convention : aucune ligne de code ne dépasse 120 caractères et aucun import n'est collé
 * ({@code ;import }). La largeur est mesurée sur le <b>code seul</b> : le contenu des littéraux de
 * chaîne, des littéraux de caractère, des commentaires et des text blocks est neutralisé avant la
 * mesure. Les bannières de commentaires Unicode et les littéraux de chaîne incassables (templates
 * MongoDB, fragments shell/yaml/json des builders, fixtures de test) sont donc volontairement
 * exemptés — seule la structure du code est contrôlée.
 */
class GeneratorSourceLayoutTest {

    private static final int MAX_LINE_LENGTH = 120;
    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");

    @Test
    void no_generator_code_line_exceeds_120_chars() {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            List<String> codeLines = stripLiteralsAndComments(read(file));
            for (int i = 0; i < codeLines.size(); i++) {
                int len = codeLines.get(i).length();
                if (len > MAX_LINE_LENGTH) {
                    violations.add(file + ":" + (i + 1) + " (code width " + len + ")");
                }
            }
        }
        assertThat(violations)
            .as("lignes de code dépassant %d caractères (hors littéraux/commentaires)", MAX_LINE_LENGTH)
            .isEmpty();
    }

    @Test
    void no_generator_source_has_glued_imports() {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            // mesure sur le code seul : un ';import ' à l'intérieur d'un littéral (assertion, fixture)
            // ne compte pas, seul un vrai enchaînement d'imports est signalé.
            for (String codeLine : stripLiteralsAndComments(read(file))) {
                if (codeLine.contains(";import ")) {
                    violations.add(file.toString());
                    break;
                }
            }
        }
        assertThat(violations)
            .as("fichiers avec des imports collés (;import )")
            .isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Path> javaFiles() {
        List<Path> files = new ArrayList<>();
        for (String root : SOURCE_ROOTS) {
            Path dir = Path.of(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    private String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Retourne le contenu ligne par ligne, le contenu des littéraux de chaîne / caractère, des
     * commentaires (ligne et bloc) et des text blocks ayant été retiré. Les délimiteurs de chaîne
     * sont conservés afin que la structure du code reste mesurable.
     */
    private List<String> stripLiteralsAndComments(String src) {
        final int NORMAL = 0;
        final int LINE_COMMENT = 1;
        final int BLOCK_COMMENT = 2;
        final int STRING = 3;
        final int CHAR = 4;
        final int TEXT_BLOCK = 5;
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int state = NORMAL;
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '\n') {
                lines.add(cur.toString());
                cur.setLength(0);
                if (state == LINE_COMMENT) {
                    state = NORMAL;
                }
                i++;
                continue;
            }
            switch (state) {
                case NORMAL:
                    if (c == '/' && next == '/') {
                        state = LINE_COMMENT;
                        i += 2;
                    } else if (c == '/' && next == '*') {
                        state = BLOCK_COMMENT;
                        i += 2;
                    } else if (c == '"' && next == '"' && i + 2 < n && src.charAt(i + 2) == '"') {
                        state = TEXT_BLOCK;
                        i += 3;
                    } else if (c == '"') {
                        cur.append('"');
                        state = STRING;
                        i++;
                    } else if (c == '\'') {
                        cur.append('\'');
                        state = CHAR;
                        i++;
                    } else {
                        cur.append(c);
                        i++;
                    }
                    break;
                case LINE_COMMENT:
                    i++;
                    break;
                case BLOCK_COMMENT:
                    if (c == '*' && next == '/') {
                        state = NORMAL;
                        i += 2;
                    } else {
                        i++;
                    }
                    break;
                case STRING:
                    if (c == '\\') {
                        i += 2;
                    } else if (c == '"') {
                        cur.append('"');
                        state = NORMAL;
                        i++;
                    } else {
                        i++;
                    }
                    break;
                case CHAR:
                    if (c == '\\') {
                        i += 2;
                    } else if (c == '\'') {
                        cur.append('\'');
                        state = NORMAL;
                        i++;
                    } else {
                        i++;
                    }
                    break;
                case TEXT_BLOCK:
                    if (c == '"' && next == '"' && i + 2 < n && src.charAt(i + 2) == '"') {
                        state = NORMAL;
                        i += 3;
                    } else {
                        i++;
                    }
                    break;
                default:
                    i++;
            }
        }
        lines.add(cur.toString());
        return lines;
    }
}
