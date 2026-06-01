package com.mr486.msplatform.serviceb.service;

import com.mr486.msplatform.serviceb.document.ResourceB;
import com.mr486.msplatform.serviceb.dto.ResourceBDto;
import com.mr486.msplatform.serviceb.repository.ResourceBRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service métier de la ressource {@code ResourceB} : lecture et création via le repository MongoDB.
 */
@Service
@RequiredArgsConstructor
public class ResourceBService {

    private final ResourceBRepository repository;

    /**
     * Retourne toutes les ressources sous forme de DTO.
     *
     * @return la liste complète des ressources, jamais {@code null}
     */
    public List<ResourceBDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Persiste une nouvelle ressource à partir de son DTO.
     *
     * @param dto les données à créer
     * @return le DTO de la ressource persistée, identifiant renseigné
     */
    public ResourceBDto create(ResourceBDto dto) {
        ResourceB entity = ResourceB.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .build();
        return toDto(repository.save(entity));
    }

    private ResourceBDto toDto(ResourceB entity) {
        return ResourceBDto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .build();
    }
}
