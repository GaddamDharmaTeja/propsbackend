package com.prospr.repository;
import java.util.*; import org.springframework.data.mongodb.repository.MongoRepository; import com.prospr.model.CategoryRule;
public interface CategoryRuleRepository extends MongoRepository<CategoryRule,String> { List<CategoryRule> findByOwnerId(String ownerId); List<CategoryRule> findByHouseholdId(String householdId); }
