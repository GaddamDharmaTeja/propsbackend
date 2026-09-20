package com.prospr.service;

import com.prospr.model.HouseholdCategory;
import com.prospr.model.TransactionEntry;
import com.prospr.repository.HouseholdCategoryRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class HouseholdCategoryService {

    private final HouseholdCategoryRepository categories;
    private final MongoTemplate mongo;

    public HouseholdCategoryService(HouseholdCategoryRepository categories, MongoTemplate mongo) {
        this.categories = categories;
        this.mongo = mongo;
    }

    public void overlay(TransactionEntry transaction) {
        if (transaction == null || transaction.householdId == null) {
            return;
        }
        List<HouseholdCategory> saved = categories.findByHouseholdId(transaction.householdId);
        String description = transaction.description == null ? "" : transaction.description.toLowerCase(Locale.ROOT);
        for (HouseholdCategory category : saved) {
            if (category.keyword != null && !category.keyword.isBlank() && description.contains(category.keyword.toLowerCase(Locale.ROOT))) {
                transaction.category = category.name;
                break;
            }
        }
        if (!transaction.income) {
            transaction.systemClassification = classificationFor(saved, transaction.category);
        }
    }

    public void reclassify(String householdId, String name, String classification) {
        if (householdId == null || name == null) {
            return;
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("householdId").is(householdId),
                Criteria.where("income").is(false),
                Criteria.where("category").regex("^" + Pattern.quote(name) + "$", "i"),
                new Criteria().orOperator(
                        Criteria.where("userClassification").is(null),
                        Criteria.where("userClassification").is("")
                )
        ));
        mongo.updateMulti(query, new Update().set("systemClassification", classification).set("classificationSource", "SYSTEM"), TransactionEntry.class);
    }

    public String classificationFor(List<HouseholdCategory> saved, String name) {
        if (name != null) {
            for (HouseholdCategory category : saved) {
                if (category.name != null && category.name.equalsIgnoreCase(name) && category.classification != null) {
                    return category.classification;
                }
            }
        }
        return CategoryCatalog.typeOf(name);
    }
}
