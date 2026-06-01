package com.mr486.msplatform.servicea.controller;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.service.ResourceAService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Contrôleur REST de la ressource {@code ResourceA}, exposé sous {@code /api/resources-a}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resources-a")
public class ResourceAController {

    private final ResourceAService service;

    /**
     * Liste toutes les ressources.
     *
     * @return la liste des ressources sous forme de DTO
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public List<ResourceADto> findAll() {
        return service.findAll();
    }

    /**
     * Crée une ressource.
     *
     * @param dto les données de la ressource à créer
     * @return la ressource créée, identifiant renseigné
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public ResourceADto create(@RequestBody ResourceADto dto) {
        return service.create(dto);
    }
}
