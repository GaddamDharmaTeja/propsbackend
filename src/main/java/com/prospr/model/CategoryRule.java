package com.prospr.model;
import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document;
@Document("category_rules") public class CategoryRule { @Id public String id; public String ownerId, householdId, keyword, category; }
