package com.mr486.generator.pipeline;

import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ZipTemplateLoader {

    private static final String TEMPLATE = "templates/ms-platform-template.zip";

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

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
