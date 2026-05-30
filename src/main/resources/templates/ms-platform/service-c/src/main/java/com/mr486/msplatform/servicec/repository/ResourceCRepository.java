package com.mr486.msplatform.servicec.repository;
import java.util.UUID;
import com.mr486.msplatform.servicec.entity.ResourceC;
import org.springframework.stereotype.Repository;import org.springframework.data.jpa.repository.JpaRepository;
@Repository
public interface ResourceCRepository extends JpaRepository<ResourceC,UUID> {}
