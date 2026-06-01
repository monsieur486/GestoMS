package com.mr486.msplatform.servicec.service;

import com.mr486.msplatform.servicec.dto.ResourceCDto;
import com.mr486.msplatform.servicec.entity.ResourceC;
import com.mr486.msplatform.servicec.repository.ResourceCRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

/**
 * Service métier de la ressource {@code ResourceC} : lecture et création via le repository JPA.
 */
@Service
@RequiredArgsConstructor
public class ResourceCService {

    private final ResourceCRepository repository;

    /**
     * Retourne toutes les ressources sous forme de DTO.
     *
     * @return la liste complète des ressources, jamais {@code null}
     */
    public List<ResourceCDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Persiste une nouvelle ressource à partir de son DTO.
     *
     * @param dto les données à créer
     * @return le DTO de la ressource persistée, identifiant renseigné
     */
    public ResourceCDto create(ResourceCDto dto) {
        ResourceC entity = ResourceC.builder()
            .name(dto.getName())
            .description(dto.getDescription())
            .build();
        return toDto(repository.save(entity));
    }

    private ResourceCDto toDto(ResourceC entity) {
        return ResourceCDto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .build();
    }
}
