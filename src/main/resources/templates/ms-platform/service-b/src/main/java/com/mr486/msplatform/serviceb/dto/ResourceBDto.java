package com.mr486.msplatform.serviceb.dto;

import lombok.*;

/**
 * DTO de transfert de la ressource {@code ResourceB}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceBDto {

    private String id;
    private String name;
    private String description;
}
