package com.prospr.repository;

import com.prospr.model.CreepBaseline;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CreepBaselineRepository extends MongoRepository<CreepBaseline, String> {
    List<CreepBaseline> findByHouseholdId(String householdId);

    Optional<CreepBaseline> findByHouseholdIdAndCategoryIgnoreCase(String householdId, String category);
}
