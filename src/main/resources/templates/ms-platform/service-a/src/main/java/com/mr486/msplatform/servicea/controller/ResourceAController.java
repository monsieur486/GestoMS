package com.mr486.msplatform.servicea.controller;
import com.mr486.msplatform.servicea.dto.ResourceADto;import com.mr486.msplatform.servicea.service.ResourceAService;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/resources-a")
public class ResourceAController{ private final ResourceAService service; @GetMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public List<ResourceADto> findAll(){return service.findAll();} @PostMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_A')") public ResourceADto create(@RequestBody ResourceADto dto){return service.create(dto);} }
