package com.mr486.msplatform.servicea.service;

import com.mr486.msplatform.servicea.dto.ResourceADto;
import com.mr486.msplatform.servicea.entity.ResourceA;
import com.mr486.msplatform.servicea.repository.ResourceARepository;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.List;
@Service @RequiredArgsConstructor
public class ResourceAService{
  private final ResourceARepository repository;
  public List<ResourceADto> findAll(){return repository.findAll().stream().map(this::toDto).toList();}
  public ResourceADto findById(Long id){return toDto(repository.findById(id).orElseThrow(()->new ResourceNotFoundException(id)));}
  public ResourceADto create(ResourceADto dto){ ResourceA entity=ResourceA.builder().name(dto.getName()).description(dto.getDescription()).build(); return toDto(repository.save(entity)); }
  public ResourceADto update(Long id,ResourceADto dto){ ResourceA entity=repository.findById(id).orElseThrow(()->new ResourceNotFoundException(id)); entity.setName(dto.getName()); entity.setDescription(dto.getDescription()); return toDto(repository.save(entity)); }
  public void delete(Long id){ if(!repository.existsById(id)) throw new ResourceNotFoundException(id); repository.deleteById(id); }
  private ResourceADto toDto(ResourceA entity){return ResourceADto.builder().id(entity.getId()).name(entity.getName()).description(entity.getDescription()).build();}
}
