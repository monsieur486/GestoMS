package com.mr486.msplatform.servicec.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Entité JPA de la ressource {@code ResourceC}, mappée sur la table {@code resources_c}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "resources_c")
public class ResourceC {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;
}
