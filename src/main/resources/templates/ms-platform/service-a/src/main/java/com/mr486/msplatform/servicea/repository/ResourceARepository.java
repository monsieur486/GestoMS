package com.mr486.msplatform.servicea.repository;

import com.mr486.msplatform.servicea.entity.ResourceA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA de l'entité {@code ResourceA} (clé primaire {@code Long}).
 */
@Repository
public interface ResourceARepository extends JpaRepository<ResourceA, Long> {
}
