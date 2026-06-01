package com.mr486.msplatform.servicea.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entité JPA de la ressource {@code ResourceA}, mappée sur la table {@code resources_a}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "resources_a")
public class ResourceA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;
}
