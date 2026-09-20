package com.prospr.config;

import com.prospr.model.User;
import com.prospr.repository.UserRepository;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Backfills householdId from legacy ownerId once, without moving or deleting records. */
@Component
public class HouseholdDataMigration {
  private static final List<String> OWNER_SCOPED = List.of("family_members", "financial_accounts", "transactions", "goals", "budgets", "settings", "category_rules", "import_batches");
  private final UserRepository users; private final MongoTemplate mongo;
  public HouseholdDataMigration(UserRepository users, MongoTemplate mongo) { this.users=users; this.mongo=mongo; }
  @EventListener(ApplicationReadyEvent.class)
  public void migrate() {
    for (User user : users.findAll()) {
      String household=user.getHouseholdId();
      if (household==null || household.isBlank()) { household=user.getId(); user.setHouseholdId(household); user.setMemberId(user.getMemberId()==null?user.getId():user.getMemberId()); user.setHouseholdCreator(true); users.save(user); }
      Query query=new Query(new Criteria().andOperator(Criteria.where("ownerId").is(user.getId()), Criteria.where("householdId").exists(false)));
      for (String collection : OWNER_SCOPED) mongo.updateMulti(query, new Update().set("householdId", household), collection);
    }
  }
}
