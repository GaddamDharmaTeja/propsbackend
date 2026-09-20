package com.prospr.model;
import java.time.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("family_members") public class FamilyMember {
  @Id public String id;
  /** ownerId is retained for legacy records; householdId is the access boundary. */
  public String ownerId;
  @Indexed public String householdId;
  public String accountId, name, relationship, gender, occupation, username, email, status;
  public String activationTokenHash;
  public LocalDateTime activationExpiresAt;
  /** Epoch millis. Used for expiry checks because LocalDateTime does not always round-trip through MongoDB. */
  public Long activationExpiryEpoch;
  public LocalDate dob;
  public LocalDateTime createdAt=LocalDateTime.now(), updatedAt=LocalDateTime.now();
  @Transient public String activationUrl;
}
