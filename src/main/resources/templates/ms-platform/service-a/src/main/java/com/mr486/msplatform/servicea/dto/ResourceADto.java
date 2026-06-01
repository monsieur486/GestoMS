package com.mr486.msplatform.servicea.dto;

import lombok.*;

/**
 * DTO de transfert de la ressource {@code ResourceA}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceADto {

    private Long id;
    private String name;
    private String description;
}
