package com.mr486.generator.controller;

import com.mr486.generator.dto.PlatformGenerationRequest;
import com.mr486.generator.service.PlatformGeneratorService;
import com.mr486.generator.zip.ZipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST unique du générateur.
 *
 * <p>Reçoit une {@link PlatformGenerationRequest} en JSON et retourne le ZIP de la plateforme générée
 * directement dans la réponse HTTP. Aucune persistance ni session : chaque appel est indépendant.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/generate")
public class PlatformGeneratorController {
    private final PlatformGeneratorService generatorService;
    private final ZipService zipService;

    /**
     * Génère la plateforme et la retourne en {@code application/zip}.
     *
     * <p>Le header {@code Content-Disposition: attachment; filename=...} utilise le champ {@code name}
     * de la requête (ou {@code "ms-platform"} si absent / vide) suffixé de {@code .zip}.
     *
     * <p><b>Exemple :</b> une requête {@code {"name":"shop"}} retourne un ZIP avec l'en-tête
     * {@code Content-Disposition: attachment; filename=shop.zip} ; sans {@code name}, le fichier
     * s'appelle {@code ms-platform.zip}.
     *
     * @param request requête JSON acceptée
     * @return ZIP binaire ; le client est censé l'écrire dans un fichier puis l'extraire
     */
    @PostMapping(value = "/platform", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@Valid @RequestBody PlatformGenerationRequest request) {
        int nbResources = request.getResources() == null ? 0 : request.getResources().size();
        log.info("génération de plateforme demandée : name={}, {} ressource(s)",
                request.getName(), nbResources);
        byte[] data = zipService.zip(generatorService.generate(request));
        String filename = (request.getName() == null || request.getName().isBlank()
                ? "ms-platform" : request.getName()) + ".zip";
        log.info("plateforme '{}' générée : {} octets", filename, data.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .body(data);
    }
}
