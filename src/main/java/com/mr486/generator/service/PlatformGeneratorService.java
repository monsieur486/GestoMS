package com.mr486.generator.service;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PlatformGeneratorService {

    private static final String TEMPLATE = "templates/ms-platform-template.zip";
    private static final String TEMPLATE_ROOT = "ms-platform";

    /**
     * V5.4 final generator.
     *
     * This version emits the validated V5.4 platform template:
     * - common-lib
     * - service-batch
     * - Rabbit JSON
     * - Redis JSON text storage
     * - WebSocket batch notifications
     * - configurable batch replicas/concurrency/delay/memory
     * - test-all.sh + tokens.env
     * - benchmark-async-batch.sh
     *
     * The generated root folder follows request.name when provided.
     */
    public List<GeneratedFile> generate(PlatformGenerationRequest request) {
        String targetRoot = normalizeName(request);
        List<GeneratedFile> files = new ArrayList<>();

        try (InputStream inputStream = new ClassPathResource(TEMPLATE).getInputStream();
             ZipInputStream zip = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String path = entry.getName();
                if (path.startsWith(TEMPLATE_ROOT + "/")) {
                    path = targetRoot + path.substring(TEMPLATE_ROOT.length());
                }

                byte[] content = readAll(zip);
                boolean executable = path.endsWith(".sh") || path.endsWith("mvnw");
                files.add(new GeneratedFile(path, content, executable));
            }

            return files;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load V5.4 platform template", ex);
        }
    }

    private String normalizeName(PlatformGenerationRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            return TEMPLATE_ROOT;
        }
        return request.getName().trim();
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
