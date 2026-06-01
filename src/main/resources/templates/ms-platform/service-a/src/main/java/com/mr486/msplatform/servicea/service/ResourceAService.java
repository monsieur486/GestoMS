package com.mr486.msplatform.servicea.service;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.entity.ResourceA;
import com.mr486.msplatform.servicea.repository.ResourceARepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service métier de la ressource {@code ResourceA} : opérations CRUD via le repository JPA.
 */
@Service
@RequiredArgsConstructor
public class ResourceAService {

    private final ResourceARepository repository;

    /**
     * Retourne toutes les ressources sous forme de DTO.
     *
     * @return la liste complète des ressources, jamais {@code null}
     */
    public List<ResourceADto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Retourne une ressource par son identifiant.
     *
     * @param id l'identifiant de la ressource
     * @return le DTO de la ressource
     * @throws ResourceNotFoundException si aucune ressource ne porte cet identifiant
     */
    public ResourceADto findById(Long id) {
        return toDto(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(id)));
    }

    /**
     * Persiste une nouvelle ressource à partir de son DTO.
     *
     * @param dto les données à créer
     * @return le DTO de la ressource persistée, identifiant renseigné
     */
    public ResourceADto create(ResourceADto dto) {
        ResourceA entity = ResourceA.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .build();
        return toDto(repository.save(entity));
    }

    /**
     * Met à jour une ressource existante.
     *
     * @param id  l'identifiant de la ressource à modifier
     * @param dto les nouvelles données
     * @return le DTO de la ressource mise à jour
     * @throws ResourceNotFoundException si aucune ressource ne porte cet identifiant
     */
    public ResourceADto update(Long id, ResourceADto dto) {
        ResourceA entity = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(id));
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        return toDto(repository.save(entity));
    }

    /**
     * Supprime une ressource par son identifiant.
     *
     * @param id l'identifiant de la ressource à supprimer
     * @throws ResourceNotFoundException si aucune ressource ne porte cet identifiant
     */
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private ResourceADto toDto(ResourceA entity) {
        return ResourceADto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .build();
    }
}
