package com.prospr.model;
import java.math.BigDecimal; import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document;
@Document("goals") public class Goal { @Id public String id; public String ownerId, householdId, title, icon; public BigDecimal targetAmount, savedAmount; public java.time.LocalDate targetDate; }
