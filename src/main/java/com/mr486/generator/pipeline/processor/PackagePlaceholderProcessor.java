package com.mr486.generator.pipeline.processor;

import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
        if (samePackage && sameJava) return files;

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
        if (containsNullByte(content)) return content;
        try {
            String text = new String(content, StandardCharsets.UTF_8);
            // longer first
            text = text.replace(SRC_BASE_PKG, tgtBasePkg);
            text = text.replace(SRC_GROUP_ID, tgtGroupId);
            if (!sameJava) {
                text = text.replace(SRC_JAVA_VER, "<java.version>" + tgtJavaVer + "</java.version>");
            }
            return text.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return content;
        }
    }

    private boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
