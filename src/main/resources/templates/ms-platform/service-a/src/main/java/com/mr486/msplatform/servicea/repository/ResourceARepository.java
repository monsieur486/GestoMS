package com.mr486.msplatform.servicea.repository;
import com.mr486.msplatform.servicea.entity.ResourceA;
import org.springframework.stereotype.Repository;import org.springframework.data.jpa.repository.JpaRepository;
@Repository
public interface ResourceARepository extends JpaRepository<ResourceA,Long> {}
