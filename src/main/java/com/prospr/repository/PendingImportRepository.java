package com.prospr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.prospr.model.PendingImport;

public interface PendingImportRepository extends MongoRepository<PendingImport, String> {

    List<PendingImport> findByHouseholdIdAndStatusOrderByCreatedAtDesc(String householdId, String status);

    Optional<PendingImport> findByIdAndHouseholdId(String id, String householdId);

    Optional<PendingImport> findByIdAndOwnerId(String id, String ownerId);
}
