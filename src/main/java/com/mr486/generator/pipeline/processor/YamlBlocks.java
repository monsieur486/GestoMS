package com.mr486.generator.pipeline.processor;

import java.util.function.Predicate;

/**
 * Retrait d'un bloc YAML par balayage de lignes (indentation-aware), sans regex multi-lignes.
 *
 * <p>Factorise la forme commune aux retraits de bloc du docker-compose et des routes de la
 * passerelle : repérer la ligne de début, puis étendre le bloc jusqu'à la première frontière
 * (ligne sœur ou désindentée), en ignorant les lignes vides, et reconstruire le texte sans ce bloc.
 *
 * <p><b>Exemple :</b> {@code removeBlock(compose, l -> l.equals("  redis:"), l -> !l.startsWith(" "))}
 * retire le service {@code redis:} et toutes ses lignes plus indentées.
 */
final class YamlBlocks {

    private YamlBlocks() {
    }

    /**
     * Retire le premier bloc dont la ligne de tête satisfait {@code isStart}, jusqu'à la première
     * ligne (non vide) satisfaisant {@code isBoundary} — ou la fin du texte.
     *
     * <p><b>Exemple :</b> si {@code isStart} ne correspond à aucune ligne, le texte est renvoyé
     * inchangé.
     *
     * @param text texte YAML complet
     * @param isStart prédicat identifiant la ligne de début du bloc
     * @param isBoundary prédicat identifiant la première ligne hors du bloc (sœur ou désindentée)
     * @return le texte privé du bloc, ou le texte inchangé si aucun début ne correspond
     */
    static String removeBlock(String text, Predicate<String> isStart, Predicate<String> isBoundary) {
        String[] lines = text.split("\n", -1);
        int startIdx = indexOfStart(lines, isStart);
        if (startIdx == -1) {
            return text;
        }
        int endIdx = indexOfBoundary(lines, startIdx, isBoundary);
        return rejoinWithout(lines, startIdx, endIdx);
    }

    // Index de la première ligne de début, ou -1 si aucune.
    private static int indexOfStart(String[] lines, Predicate<String> isStart) {
        for (int i = 0; i < lines.length; i++) {
            if (isStart.test(lines[i])) {
                return i;
            }
        }
        return -1;
    }

    // Index de la première frontière après le début (lignes vides ignorées), ou la fin du texte.
    private static int indexOfBoundary(String[] lines, int startIdx, Predicate<String> isBoundary) {
        for (int i = startIdx + 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isEmpty() && isBoundary.test(line)) {
                return i;
            }
        }
        return lines.length;
    }

    // Recolle les lignes en omettant l'intervalle [startIdx, endIdx).
    private static String rejoinWithout(String[] lines, int startIdx, int endIdx) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i >= startIdx && i < endIdx) {
                continue;
            }
            out.append(lines[i]);
            if (i < lines.length - 1) {
                out.append("\n");
            }
        }
        return out.toString();
    }
}
