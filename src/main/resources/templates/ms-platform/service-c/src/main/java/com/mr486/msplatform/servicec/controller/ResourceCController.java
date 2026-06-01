package com.mr486.msplatform.servicec.controller;

import com.mr486.msplatform.servicec.dto.ResourceCDto;
import com.mr486.msplatform.servicec.service.ResourceCService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Contrôleur REST de la ressource {@code ResourceC}, exposé sous {@code /api/resources-c}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resources-c")
public class ResourceCController {

    private final ResourceCService service;

    /**
     * Liste toutes les ressources.
     *
     * @return la liste des ressources sous forme de DTO
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_C')")
    public List<ResourceCDto> findAll() {
        return service.findAll();
    }

    /**
     * Crée une ressource.
     *
     * @param dto les données de la ressource à créer
     * @return la ressource créée, identifiant renseigné
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_C')")
    public ResourceCDto create(@RequestBody ResourceCDto dto) {
        return service.create(dto);
    }
}
