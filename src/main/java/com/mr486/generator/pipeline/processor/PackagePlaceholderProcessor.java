package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Substitue les placeholders d'identité Maven et Java du modèle par les valeurs de la requête.
 * <ul>
 *   <li>{@code com.mr486.msplatform} → {@code basePackage}
 *       (dans le contenu Java et dans les chemins {@code com/mr486/msplatform/}).</li>
 *   <li>{@code com.mr486} → {@code groupId} (dans les pom et les chemins {@code com/mr486/}).</li>
 *   <li>{@code <java.version>17</java.version>} → version Java demandée.</li>
 * </ul>
 * Court-circuité (no-op) si les trois champs de la requête sont à leur valeur par défaut.
 */
@Component
@Order(30)
public class PackagePlaceholderProcessor implements FileProcessor {

    private static final String SRC_BASE_PKG  = "com.mr486.msplatform";
    private static final String SRC_GROUP_ID  = "com.mr486";
    private static final String SRC_BASE_PATH = "com/mr486/msplatform/";
    private static final String SRC_GID_PATH  = "com/mr486/";
    private static final String SRC_JAVA_VER  = "<java.version>17</java.version>";

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        String tgtBasePkg  = ctx.getRequest().getBasePackage();
        String tgtGroupId  = ctx.getRequest().getGroupId();
        String tgtJavaVer  = ctx.getRequest().getJavaVersion();
        String tgtBasePath = tgtBasePkg.replace('.', '/') + "/";
        String tgtGidPath  = tgtGroupId.replace('.', '/') + "/";

        boolean samePackage = SRC_BASE_PKG.equals(tgtBasePkg) && SRC_GROUP_ID.equals(tgtGroupId);
        boolean sameJava    = "17".equals(tgtJavaVer);
        if (samePackage && sameJava) {
            return files;
        }

        return files.stream()
            .map(f -> transform(f, tgtBasePkg, tgtGroupId, tgtBasePath, tgtGidPath, tgtJavaVer, sameJava))
            .toList();
    }

    private GeneratedFile transform(GeneratedFile f,
                                    String tgtBasePkg, String tgtGroupId,
                                    String tgtBasePath, String tgtGidPath,
                                    String tgtJavaVer, boolean sameJava) {
        String newPath = transformPath(f.path(), tgtBasePath, tgtGidPath);
        byte[] newContent = transformContent(f.content(), tgtBasePkg, tgtGroupId, tgtJavaVer, sameJava);
        return new GeneratedFile(newPath, newContent, f.executable());
    }

    private String transformPath(String path, String tgtBasePath, String tgtGidPath) {
        // longer first to avoid partial replacement
        path = path.replace(SRC_BASE_PATH, tgtBasePath);
        path = path.replace(SRC_GID_PATH, tgtGidPath);
        return path;
    }

    private byte[] transformContent(byte[] content, String tgtBasePkg, String tgtGroupId,
                                    String tgtJavaVer, boolean sameJava) {
        if (ProcessorUtils.containsNullByte(content)) {
            return content;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        text = text.replace(SRC_BASE_PKG, tgtBasePkg);
        text = text.replace(SRC_GROUP_ID, tgtGroupId);
        if (!sameJava) {
            text = text.replace(SRC_JAVA_VER, "<java.version>" + tgtJavaVer + "</java.version>");
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
