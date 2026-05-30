package com.mr486.msplatform.serviceb.document;
import lombok.*;import org.springframework.data.annotation.Id;import org.springframework.data.mongodb.core.mapping.Document;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @Document(collection="resources_b")
public class ResourceB{ @Id private String id; private String name; private String description; }
