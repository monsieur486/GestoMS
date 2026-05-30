package com.mr486.msplatform.servicea.service;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.entity.ResourceA;
import com.mr486.msplatform.servicea.repository.ResourceARepository;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.List;
@Service @RequiredArgsConstructor
public class ResourceAService{ private final ResourceARepository repository; public List<ResourceADto> findAll(){return repository.findAll().stream().map(this::toDto).toList();} public ResourceADto create(ResourceADto dto){ ResourceA entity=ResourceA.builder().name(dto.getName()).description(dto.getDescription()).build(); return toDto(repository.save(entity)); } private ResourceADto toDto(ResourceA entity){return ResourceADto.builder().id(entity.getId()).name(entity.getName()).description(entity.getDescription()).build();}}
