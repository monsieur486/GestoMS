package com.mr486.generator.pipeline.processor;

import com.mr486.generator.dto.DatabaseType;
import com.mr486.generator.dto.IdType;
import com.mr486.generator.dto.ResourceModuleRequest;
import com.mr486.generator.model.GenerationContext;
import com.mr486.generator.pipeline.FileProcessor;
import com.mr486.generator.zip.GeneratedFile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(50)
public class ResourceExpandProcessor implements FileProcessor {

    @Override
    public List<GeneratedFile> process(List<GeneratedFile> files, GenerationContext ctx) {
        List<ResourceModuleRequest> resources = ctx.getRequest().getResources();
        if (resources == null || resources.isEmpty()) return files;

        String root = ctx.getTargetRoot();
        List<GeneratedFile> serviceATemplate = extractServiceATemplate(files, root);
        List<GeneratedFile> filtered = removeDefaultServices(files, root);

        List<GeneratedFile> result = new ArrayList<>(filtered);
        for (ResourceModuleRequest res : resources) {
            result.addAll(generateService(serviceATemplate, res, root, ctx));
        }
        return result;
    }

    // ── Extract service-a template (excluding patch subdirs) ──────────────────

    private List<GeneratedFile> extractServiceATemplate(List<GeneratedFile> files, String root) {
        String serviceAPrefix = root + "/service-a/";
        String patchBatch    = serviceAPrefix + "service-batch/";
        String patchConsumer = serviceAPrefix + "service-consumer/";
        return files.stream()
            .filter(f -> f.path().startsWith(serviceAPrefix))
            .filter(f -> !f.path().startsWith(patchBatch))
            .filter(f -> !f.path().startsWith(patchConsumer))
            .collect(Collectors.toList());
    }

    private List<GeneratedFile> removeDefaultServices(List<GeneratedFile> files, String root) {
        return files.stream()
            .filter(f -> !isDefaultService(f.path(), root))
            .collect(Collectors.toList());
    }

    private boolean isDefaultService(String path, String root) {
        String rel = relative(path, root);
        return rel.startsWith("service-a/")
            || rel.startsWith("service-b/")
            || rel.startsWith("service-c/");
    }

    // ── Generate one service ──────────────────────────────────────────────────

    private List<GeneratedFile> generateService(List<GeneratedFile> template,
                                                ResourceModuleRequest res,
                                                String root,
                                                GenerationContext ctx) {
        String basePackage = ctx.getRequest().getBasePackage();
        List<GeneratedFile> generated = new ArrayList<>();
        for (GeneratedFile f : template) {
            String newPath    = transformPath(f.path(), res, root);
            byte[] newContent = transformContent(f.content(), res);
            newContent = applyDatabaseType(newPath, newContent, res, basePackage);
            if (newContent == null) continue;  // file removed (e.g., changelog for Mongo)
            newPath    = applyMongoPathRename(newPath, res);
            newContent = applyIdType(newPath, newContent, res);
            generated.add(new GeneratedFile(newPath, newContent, f.executable()));
        }
        return generated;
    }

    // ── Path transformations ──────────────────────────────────────────────────

    private String transformPath(String path, ResourceModuleRequest res, String root) {
        String serviceClass   = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        // More specific replacements first
        path = path.replace(root + "/service-a/", root + "/" + res.getServiceName() + "/");
        path = path.replace("/servicea/", "/" + servicePackage + "/");
        path = path.replace("ServiceA", serviceClass);
        path = path.replace("ResourceA", res.getClassName());
        return path;
    }

    private String applyMongoPathRename(String path, ResourceModuleRequest res) {
        if (res.getDatabaseType() == DatabaseType.MONGO) {
            path = path.replace("/entity/", "/document/");
        }
        return path;
    }

    // ── Content transformations ───────────────────────────────────────────────

    private byte[] transformContent(byte[] content, ResourceModuleRequest res) {
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        text = applyBaseReplacements(text, res);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private String applyBaseReplacements(String text, ResourceModuleRequest res) {
        String serviceClass   = toPascalCase(res.getServiceName());
        String servicePackage = toConcatLower(res.getServiceName());
        String serviceSnake   = res.getServiceName().replace("-", "_");
        String serviceScream  = serviceSnake.toUpperCase();
        String entityPlural   = res.getClassName().toLowerCase() + "s";
        String entityLower    = res.getClassName().toLowerCase();

        // Longest/most-specific first
        text = text.replace("USER_SERVICE_A",    "USER_" + serviceScream);
        text = text.replace("SERVICE_A",          serviceScream);
        text = text.replace("resources_a",        entityPlural);
        text = text.replace("resource_a",         entityLower);
        text = text.replace("/api/resources-a",   res.getRoutePrefix());
        text = text.replace("service-a",          res.getServiceName());
        text = text.replace("service_a",          serviceSnake);
        text = text.replace("servicea",           servicePackage);
        text = text.replace("ServiceA",           serviceClass);
        text = text.replace("ResourceA",          res.getClassName());
        return text;
    }

    // ── DatabaseType (Tasks 8 and 9) ──────────────────────────────────────────

    protected byte[] applyDatabaseType(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        if (res.getDatabaseType() == null || res.getDatabaseType() == DatabaseType.POSTGRES) return content;
        if (res.getDatabaseType() == DatabaseType.H2)    return applyH2(path, content, res);
        if (res.getDatabaseType() == DatabaseType.MONGO) return applyMongo(path, content, res, basePackage);
        return content;
    }

    private byte[] applyH2(String path, byte[] content, ResourceModuleRequest res) {
        if (containsNullByte(content)) return content;
        String text = new String(content, StandardCharsets.UTF_8);
        String dbName = res.getServiceName().replace("-", "") + "db";

        if (path.endsWith("pom.xml")) {
            text = text.replace(
                "<groupId>org.postgresql</groupId><artifactId>postgresql</artifactId>",
                "<groupId>com.h2database</groupId><artifactId>h2</artifactId>"
            );
        }
        if (path.endsWith("application.yml")) {
            String serviceSnake = res.getServiceName().replace("-", "_").toUpperCase();
            text = text.replace(
                "url: ${" + serviceSnake + "_DATASOURCE_URL:jdbc:postgresql://localhost:5432/" + res.getServiceName().replace("-","_") + "_db}",
                "url: jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            );
            text = text.replace(
                "username: ${" + serviceSnake + "_DB_USERNAME:" + res.getServiceName().replace("-","_") + "}",
                "username: sa"
            );
            text = text.replace(
                "password: ${" + serviceSnake + "_DB_PASSWORD:" + res.getServiceName().replace("-","_") + "}",
                "password:"
            );
            text = text.replace("driver-class-name: org.postgresql.Driver", "driver-class-name: org.h2.Driver");
            // Add H2 console config after datasource block
            if (!text.contains("h2:") && text.contains("driver-class-name: org.h2.Driver")) {
                text = text.replace("driver-class-name: org.h2.Driver",
                    "driver-class-name: org.h2.Driver\n  h2:\n    console:\n      enabled: true");
            }
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] applyMongo(String path, byte[] content, ResourceModuleRequest res, String basePackage) {
        // Implemented in Task 9
        return content;
    }

    // ── IdType (Task 10) ──────────────────────────────────────────────────────

    protected byte[] applyIdType(String path, byte[] content, ResourceModuleRequest res) {
        if (res.getIdType() == null || res.getIdType() == IdType.LONG) return content;
        // INTEGER and UUID implemented in Task 10
        return content;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    protected String toPascalCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("[-_]")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    protected String toConcatLower(String kebab) {
        return kebab.replace("-", "").replace("_", "").toLowerCase();
    }

    protected String relative(String path, String root) {
        String prefix = root + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    protected boolean containsNullByte(byte[] content) {
        for (byte b : content) if (b == 0) return true;
        return false;
    }
}
