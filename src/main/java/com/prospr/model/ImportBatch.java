package com.prospr.model;
import java.time.*; import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.mapping.Document;
@Document("import_batches") public class ImportBatch { @Id public String id; public String ownerId, householdId, accountId, filename, checksum, sourceType, status, mapping; public int parsedCount, importedCount, skippedCount; public LocalDateTime createdAt=LocalDateTime.now(), completedAt; }
