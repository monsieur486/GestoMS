package com.mr486.msplatform.serviceb.repository;
import com.mr486.msplatform.serviceb.document.ResourceB;
import org.springframework.stereotype.Repository;import org.springframework.data.mongodb.repository.MongoRepository;
@Repository
public interface ResourceBRepository extends MongoRepository<ResourceB,String> {}
