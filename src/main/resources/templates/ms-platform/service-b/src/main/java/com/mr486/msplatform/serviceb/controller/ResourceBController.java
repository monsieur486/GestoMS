package com.mr486.msplatform.serviceb.controller;

import com.mr486.msplatform.serviceb.dto.ResourceBDto;
import com.mr486.msplatform.serviceb.service.ResourceBService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * Contrôleur REST de la ressource {@code ResourceB}, exposé sous {@code /api/resources-b}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resources-b")
public class ResourceBController {

    private final ResourceBService service;

    /**
     * Liste toutes les ressources.
     *
     * @return la liste des ressources sous forme de DTO
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_B')")
    public List<ResourceBDto> findAll() {
        return service.findAll();
    }

    /**
     * Crée une ressource.
     *
     * @param dto les données de la ressource à créer
     * @return la ressource créée, identifiant renseigné
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_B')")
    public ResourceBDto create(@RequestBody ResourceBDto dto) {
        return service.create(dto);
    }
}
