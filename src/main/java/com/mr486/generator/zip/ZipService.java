package com.mr486.generator.zip;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;

/**
 * Sérialise la liste des fichiers produits par le pipeline en une archive ZIP en mémoire.
 *
 * <p>Utilise Apache Commons Compress plutôt que {@code java.util.zip} pour préserver le bit Unix
 * exécutable des fichiers concernés (mvnw, scripts shell).
 */
@Service
public class ZipService {
    /**
     * Crée le ZIP final. Les chemins sont écrits tels quels, sans ré-encodage ; le bit exécutable
     * Unix {@code 0755} est réappliqué aux entrées marquées {@link GeneratedFile#executable()}.
     *
     * @param files liste ordonnée des fichiers à empaqueter
     * @return octets du ZIP, prêts à être renvoyés au client HTTP
     */
    public byte[] zip(List<GeneratedFile> files) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            for (GeneratedFile file : files) {
                ZipArchiveEntry entry = new ZipArchiveEntry(file.path());
                if (file.executable()) {
                    entry.setUnixMode(0755);
                }
                zip.putArchiveEntry(entry);
                zip.write(file.content());
                zip.closeArchiveEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create ZIP", ex);
        }
    }
}
