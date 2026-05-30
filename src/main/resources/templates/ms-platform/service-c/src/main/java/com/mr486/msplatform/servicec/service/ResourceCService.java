package com.mr486.msplatform.servicec.service;

import java.util.UUID;
import com.mr486.msplatform.servicec.dto.ResourceCDto;
import com.mr486.msplatform.servicec.entity.ResourceC;
import com.mr486.msplatform.servicec.repository.ResourceCRepository;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.List;
@Service @RequiredArgsConstructor
public class ResourceCService{ private final ResourceCRepository repository; public List<ResourceCDto> findAll(){return repository.findAll().stream().map(this::toDto).toList();} public ResourceCDto create(ResourceCDto dto){ ResourceC entity=ResourceC.builder().name(dto.getName()).description(dto.getDescription()).build(); return toDto(repository.save(entity)); } private ResourceCDto toDto(ResourceC entity){return ResourceCDto.builder().id(entity.getId()).name(entity.getName()).description(entity.getDescription()).build();}}
