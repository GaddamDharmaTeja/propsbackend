package com.prospr.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("household_categories")
@CompoundIndex(name = "household_category_name", def = "{'householdId': 1, 'name': 1}")
public class HouseholdCategory {
    @Id
    public String id;
    public String householdId;
    public String name;
    public String classification;
    public String keyword;
    public boolean custom;
}
