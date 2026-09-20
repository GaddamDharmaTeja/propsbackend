package com.prospr.model;
import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document;
@Document("financial_accounts") public class FinancialAccount { @Id public String id; public String ownerId, householdId, institution, accountName, accountLastFour; public java.time.LocalDateTime createdAt=java.time.LocalDateTime.now(); }
