package com.prospr.model;
import java.util.*; import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document;
@Document("settings") public class UserSettings { @Id public String id; public String ownerId, householdId, theme="light", primaryBank, monthlyIncome; public List<String> goals=new ArrayList<>(), notifications=new ArrayList<>(); }
