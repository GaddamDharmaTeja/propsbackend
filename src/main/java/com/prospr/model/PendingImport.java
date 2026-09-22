package com.prospr.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Statement upload that automatic parsing could not map.
 * User updates format (import template), then retries.
 */
@Document("pending_imports")
public class PendingImport {

    @Id
    public String id;

    public String ownerId;
    public String householdId;
    public String accountId;

    public String filename;
    public String contentType;
    public String sourceType;
    public String checksum;

    /** Raw statement bytes for later re-parse / analyze. */
    public byte[] fileBytes;

    public String statementPassword;
    public String reason;
    public String status = "NEEDS_FORMAT";

    public String resolvedTemplateId;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime resolvedAt;
}
