package com.mr486.msplatform.serviceb.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Document MongoDB de la ressource {@code ResourceB}, mappé sur la collection {@code resources_b}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "resources_b")
public class ResourceB {

    @Id
    private String id;

    private String name;

    private String description;
}
