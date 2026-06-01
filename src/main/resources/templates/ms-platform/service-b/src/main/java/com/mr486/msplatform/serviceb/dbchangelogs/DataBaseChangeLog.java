package com.mr486.msplatform.serviceb.dbchangelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.List;

/**
 * Changelog Mongock d'initialisation de la collection {@code resources_b} pour {@code service-b}.
 */
@ChangeUnit(id = "seed-service-b", order = "001", author = "mr486")
public class DataBaseChangeLog {

    private final MongoTemplate mongoTemplate;

    public DataBaseChangeLog(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Insère les données initiales dans la collection {@code resources_b} si elle est vide.
     */
    @Execution
    public void seed() {
        if (mongoTemplate.getCollection("resources_b").countDocuments() > 0) {
            return;
        }
        mongoTemplate.getCollection("resources_b").insertMany(List.of(
            new Document("name", "ResourceB 1").append("description", "Premiere ressource MongoDB"),
            new Document("name", "ResourceB 2").append("description", "Deuxieme ressource MongoDB")
        ));
    }

    /**
     * Supprime tous les documents insérés lors de l'exécution ({@link #seed()}).
     */
    @RollbackExecution
    public void rollback() {
        mongoTemplate.getCollection("resources_b").deleteMany(new Document());
    }
}
