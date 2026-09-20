package com.prospr.repository;

import com.prospr.model.ImportTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ImportTemplateRepository
        extends MongoRepository<ImportTemplate, String> {

    List<ImportTemplate> findByOwnerIdAndActiveTrue(
            String ownerId
    );

    Optional<ImportTemplate> findByIdAndOwnerId(
            String id,
            String ownerId
    );
}