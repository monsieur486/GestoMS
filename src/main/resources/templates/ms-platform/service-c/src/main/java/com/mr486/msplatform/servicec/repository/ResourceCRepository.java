package com.mr486.msplatform.servicec.repository;

import com.mr486.msplatform.servicec.entity.ResourceC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/**
 * Repository JPA de l'entité {@code ResourceC} (clé primaire {@code UUID}).
 */
@Repository
public interface ResourceCRepository extends JpaRepository<ResourceC, UUID> {
}
