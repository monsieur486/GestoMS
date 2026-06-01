package com.mr486.msplatform.servicea.controller;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.service.ResourceAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * Retourne une ressource par son identifiant.
     *
     * @param id l'identifiant de la ressource
     * @return le DTO de la ressource
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public ResourceADto findById(@PathVariable Long id) {
        return service.findById(id);
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

    /**
     * Met à jour une ressource existante.
     *
     * @param id  l'identifiant de la ressource à modifier
     * @param dto les nouvelles données
     * @return le DTO de la ressource mise à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public ResourceADto update(@PathVariable Long id, @RequestBody ResourceADto dto) {
        return service.update(id, dto);
    }

    /**
     * Supprime une ressource par son identifiant.
     *
     * @param id l'identifiant de la ressource à supprimer
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
