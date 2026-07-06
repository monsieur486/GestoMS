package com.mr486.generator.pipeline;

import com.mr486.generator.zip.GeneratedFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Charge le modèle de plateforme depuis le répertoire {@code templates/ms-platform/} présent
 * sur le classpath et l'expose comme une liste de {@link GeneratedFile}. Première étape
 * (implicite, hors pipeline) avant l'application des {@link FileProcessor}.
 *
 * <p>Le modèle est stocké décompressé (un fichier = une entrée), ce qui rend ses diffs Git lisibles.
 * L'énumération passe par {@link PathMatchingResourcePatternResolver}, ce qui fonctionne aussi bien
 * quand l'application tourne depuis {@code target/classes} que packagée dans un jar exécutable.
 *
 * <p>Les répertoires sont ignorés et le bit exécutable Unix est dérivé heuristiquement de l'extension
 * {@code .sh} ou du nom {@code mvnw} (les permissions ne survivent pas au classpath).
 *
 * <p><b>Convention dotfiles</b> : le modèle ne contient aucun vrai fichier caché. Les dotfiles sont
 * stockés avec un préfixe {@code dot-} ({@code dot-gitignore}, {@code dot-env}) et décodés ici en
 * {@code .gitignore} / {@code .env}. Sans cela, les exclusions par défaut de plexus
 * ({@code **}{@code /.gitignore}) appliquées par {@code maven-resources-plugin} <i>et</i>
 * {@code maven-jar-plugin} les écarteraient silencieusement du jar — donc de la génération.
 */
@Component
public final class TemplateLoader {

    /** Préfixe classpath du modèle ; tout chemin émis commence par {@code ms-platform/}. */
    private static final String TEMPLATE_PREFIX = "templates/ms-platform/";
    private static final String LOCATION_PATTERN = "classpath*:" + TEMPLATE_PREFIX + "**";

    private final List<GeneratedFile> cache;

    /**
     * Charge le modèle de plateforme depuis le classpath une seule fois, au démarrage.
     */
    public TemplateLoader() {
        this.cache = loadFromClasspath();
    }

    /**
     * Retourne la liste des fichiers du modèle, chargée une seule fois au démarrage.
     *
     * @return liste immuable des fichiers du modèle
     */
    public List<GeneratedFile> load() {
        return cache;
    }

    private static List<GeneratedFile> loadFromClasspath() {
        Map<String, GeneratedFile> byPath = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION_PATTERN);
            for (Resource r : resources) {
                if (!r.isReadable()) {
                    continue;                 // écarte les répertoires (filesystem)
                }
                String url = r.getURL().toString();
                int idx = url.indexOf(TEMPLATE_PREFIX);
                if (idx < 0) {
                    continue;
                }
                String path = url.substring(idx + "templates/".length());  // -> "ms-platform/..."
                if (path.isEmpty() || path.endsWith("/")) {
                    continue;        // écarte les répertoires (jar)
                }
                path = decodeDotfile(path);
                byte[] content = r.getContentAsByteArray();
                boolean executable = path.endsWith(".sh") || path.endsWith("mvnw");
                byPath.putIfAbsent(path, new GeneratedFile(path, content, executable));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load platform template", ex);
        }
        if (byPath.isEmpty()) {
            throw new IllegalStateException("Platform template is empty or missing: " + LOCATION_PATTERN);
        }
        List<GeneratedFile> files = new ArrayList<>(byPath.values());
        files.sort(Comparator.comparing(GeneratedFile::path));
        return files;
    }

    /** Décode le préfixe {@code dot-} du dernier segment en {@code .} (voir convention dotfiles). */
    private static String decodeDotfile(String path) {
        int slash = path.lastIndexOf('/');
        String dir = slash >= 0 ? path.substring(0, slash + 1) : "";
        String name = path.substring(slash + 1);
        if (name.startsWith("dot-")) {
            name = "." + name.substring("dot-".length());
        }
        return dir + name;
    }
}

