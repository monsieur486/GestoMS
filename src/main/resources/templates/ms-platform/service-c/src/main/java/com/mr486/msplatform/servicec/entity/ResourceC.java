package com.mr486.msplatform.servicec.entity;
import java.util.UUID;
import jakarta.persistence.*;import lombok.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @Entity @Table(name="resources_c")
public class ResourceC{ @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false) private String name; @Column(nullable=false) private String description; }
