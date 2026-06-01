package com.mr486.msplatform.servicea.service;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.entity.ResourceA;
import com.mr486.msplatform.servicea.repository.ResourceARepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service métier de la ressource {@code ResourceA} : lecture et création via le repository JPA.
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

    private ResourceADto toDto(ResourceA entity) {
        return ResourceADto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .description(entity.getDescription())
            .build();
    }
}
