package com.mr486.msplatform.servicea.controller;
import com.mr486.msplatform.servicea.dto.ResourceADto;import com.mr486.msplatform.servicea.service.ResourceAService;import lombok.RequiredArgsConstructor;import org.springframework.http.HttpStatus;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/resources-a")
public class ResourceAController{
  private final ResourceAService service;
  @GetMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public List<ResourceADto> findAll(){return service.findAll();}
  @GetMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto findById(@PathVariable Long id){return service.findById(id);}
  @PostMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto create(@RequestBody ResourceADto dto){return service.create(dto);}
  @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto update(@PathVariable Long id,@RequestBody ResourceADto dto){return service.update(id,dto);}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public void delete(@PathVariable Long id){service.delete(id);}
}
