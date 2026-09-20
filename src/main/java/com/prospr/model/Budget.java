package com.prospr.model;
import java.math.BigDecimal; import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document;
@Document("budgets") public class Budget { @Id public String id; public String ownerId, householdId, category, month; public BigDecimal amount; }
