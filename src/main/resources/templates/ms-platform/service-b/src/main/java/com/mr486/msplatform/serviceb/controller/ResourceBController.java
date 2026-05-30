package com.mr486.msplatform.serviceb.controller;
import com.mr486.msplatform.serviceb.dto.ResourceBDto;import com.mr486.msplatform.serviceb.service.ResourceBService;import lombok.RequiredArgsConstructor;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/resources-b")
public class ResourceBController{ private final ResourceBService service; @GetMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_B')") public List<ResourceBDto> findAll(){return service.findAll();} @PostMapping @PreAuthorize("hasRole('ADMIN') or hasRole('USER_SERVICE_B')") public ResourceBDto create(@RequestBody ResourceBDto dto){return service.create(dto);} }
