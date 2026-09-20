package com.prospr.dto;

import com.prospr.model.FamilyMember;
import java.time.LocalDate;

/** Safe roster representation. activationUrl is populated only by creator-only issue endpoints. */
public record FamilyMemberResponse(String id, String name, String relationship, String gender, String occupation,
    String username, String email, String status, String accountId, LocalDate dob, String activationUrl) {
  public static FamilyMemberResponse roster(FamilyMember member) {
    return new FamilyMemberResponse(member.id, member.name, member.relationship, member.gender, member.occupation,
      member.username, member.email, member.status, member.accountId, member.dob, null);
  }
  public static FamilyMemberResponse issued(FamilyMember member, String activationUrl) {
    FamilyMemberResponse safe=roster(member);
    return new FamilyMemberResponse(safe.id(),safe.name(),safe.relationship(),safe.gender(),safe.occupation(),safe.username(),safe.email(),safe.status(),safe.accountId(),safe.dob(),activationUrl);
  }
}
