package com.mr486.msplatform.servicec.dto;

import lombok.*;
import java.util.UUID;

/**
 * DTO de transfert de la ressource {@code ResourceC}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceCDto {

    private UUID id;
    private String name;
    private String description;
}
