package com.mr486.msplatform.serviceb.repository;

import com.mr486.msplatform.serviceb.document.ResourceB;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository MongoDB du document {@code ResourceB} (clé primaire {@code String}).
 */
@Repository
public interface ResourceBRepository extends MongoRepository<ResourceB, String> {
}
