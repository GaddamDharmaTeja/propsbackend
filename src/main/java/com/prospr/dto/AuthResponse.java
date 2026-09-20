package com.prospr.dto;
public record AuthResponse(String token, UserProfile user) {
    public record UserProfile(String id, String memberId, String householdId, String fullName, String username, String email, String mobile, String gender, boolean householdCreator) {}
}
