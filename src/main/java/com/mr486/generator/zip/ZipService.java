package com.mr486.generator.zip;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ZipService {
    public byte[] zip(List<GeneratedFile> files) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            for (GeneratedFile file : files) {
                ZipArchiveEntry entry = new ZipArchiveEntry(file.path());
                if (file.executable()) entry.setUnixMode(0755);
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
