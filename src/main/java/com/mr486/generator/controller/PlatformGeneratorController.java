package com.mr486.generator.controller;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.service.PlatformGeneratorService;
import com.mr486.generator.zip.ZipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generate")
public class PlatformGeneratorController {
    private final PlatformGeneratorService generatorService;
    private final ZipService zipService;

    @PostMapping(value = "/platform", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@RequestBody PlatformGenerationRequest request) {
        byte[] data = zipService.zip(generatorService.generate(request));
        String filename = (request.getName() == null || request.getName().isBlank() ? "ms-platform" : request.getName()) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(data);
    }
}
