package com.prospr.repository;
import java.util.*; import org.springframework.data.mongodb.repository.MongoRepository; import com.prospr.model.UserSettings;
public interface UserSettingsRepository extends MongoRepository<UserSettings,String> { Optional<UserSettings> findByOwnerId(String ownerId); Optional<UserSettings> findByHouseholdId(String householdId); }
