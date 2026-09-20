package com.prospr.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document("creep_baselines")
@CompoundIndex(name = "household_baseline_category", def = "{'householdId': 1, 'category': 1}")
public class CreepBaseline {
    @Id
    public String id;
    public String householdId;
    public String category;
    public BigDecimal monthlyAmount;
}
