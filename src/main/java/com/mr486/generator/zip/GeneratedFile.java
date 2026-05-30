package com.mr486.generator.zip;

/**
 * Unité de travail du pipeline : un fichier identifié par son chemin relatif et son contenu binaire.
 * <p>
 * Sert à la fois de modèle interne pour le pipeline et de format de sortie de
 * {@link com.mr486.generator.pipeline.TemplateLoader}. Le drapeau {@code executable} marque le
 * bit d'exécution Unix à réappliquer à la sortie (utile pour {@code mvnw}, {@code *.sh}).
 *
 * @param path       chemin relatif dans le modèle (ex: {@code "ms-platform/pom.xml"})
 * @param content    octets bruts ; peut être binaire (image, JAR imbriqué) ou textuel UTF-8
 * @param executable {@code true} si le bit exécutable doit être réappliqué à la sortie
 */
public record GeneratedFile(String path, byte[] content, boolean executable) {}
