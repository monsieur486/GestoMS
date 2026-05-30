package com.mr486.msplatform.servicec.dto;
import java.util.UUID;
import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResourceCDto{ private UUID id; private String name; private String description; }
