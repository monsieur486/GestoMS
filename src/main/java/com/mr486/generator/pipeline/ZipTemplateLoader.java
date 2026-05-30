package com.mr486.generator.pipeline;

import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Charge le ZIP modèle de plateforme depuis le classpath et l'expose comme une liste de
 * {@link GeneratedFile}. Première étape (implicite, hors pipeline) avant l'application des
 * {@link FileProcessor}.
 * <p>
 * Les entrées de type dossier sont ignorées (elles seront recréées au repackaging). Le bit
 * exécutable Unix est dérivé heuristiquement de l'extension {@code .sh} ou du nom {@code mvnw}.
 */
@Component
public class ZipTemplateLoader {

    private static final String TEMPLATE = "templates/ms-platform-template.zip";

    /**
     * Lit le ZIP modèle et retourne la liste de ses fichiers (hors dossiers).
     *
     * @return liste mutable des fichiers du modèle, ordonnés tels qu'ils apparaissent dans le ZIP
     * @throws IllegalStateException si le ZIP est manquant ou illisible
     */
    public List<GeneratedFile> load() {
        List<GeneratedFile> files = new ArrayList<>();
        try (InputStream is = new ClassPathResource(TEMPLATE).getInputStream();
             ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String path = entry.getName();
                byte[] content = readAll(zip);
                boolean executable = path.endsWith(".sh") || path.endsWith("mvnw");
                files.add(new GeneratedFile(path, content, executable));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load platform template", ex);
        }
        return files;
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
