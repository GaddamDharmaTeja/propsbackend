package com.prospr.repository;

import com.prospr.model.HouseholdCategory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HouseholdCategoryRepository extends MongoRepository<HouseholdCategory, String> {
    List<HouseholdCategory> findByHouseholdId(String householdId);

    Optional<HouseholdCategory> findByHouseholdIdAndNameIgnoreCase(String householdId, String name);
}
