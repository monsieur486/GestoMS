package com.mr486.generator.controller;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.service.PlatformGeneratorService;
import com.mr486.generator.zip.ZipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint REST unique du générateur.
 * <p>
 * Reçoit une {@link PlatformGenerationRequest} en JSON et retourne le ZIP de la plateforme générée
 * directement dans la réponse HTTP. Aucune persistance ni session : chaque appel est indépendant.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/generate")
public class PlatformGeneratorController {
    private final PlatformGeneratorService generatorService;
    private final ZipService zipService;

    /**
     * Génère la plateforme et la retourne en {@code application/zip}.
     * <p>
     * Le header {@code Content-Disposition: attachment; filename=...} utilise le champ {@code name}
     * de la requête (ou {@code "ms-platform"} si absent / vide) suffixé de {@code .zip}.
     *
     * @param request requête JSON acceptée
     * @return ZIP binaire ; le client est censé l'écrire dans un fichier puis l'extraire
     */
    @PostMapping(value = "/platform", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@RequestBody PlatformGenerationRequest request) {
        byte[] data = zipService.zip(generatorService.generate(request));
        String filename = (request.getName() == null || request.getName().isBlank() ? "ms-platform" : request.getName()) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(data);
    }
}
