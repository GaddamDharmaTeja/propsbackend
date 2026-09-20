package com.prospr.service;

import com.prospr.model.User;
import com.prospr.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated account to its shared household. */
@Service
public class HouseholdAccess {
  private final UserRepository users;
  public HouseholdAccess(UserRepository users) { this.users = users; }
  public User user(String accountId) {
    if (accountId == null || accountId.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
    return users.findById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your account no longer exists."));
  }
  public String householdId(String accountId) {
    User user = user(accountId);
    return user.getHouseholdId() == null || user.getHouseholdId().isBlank() ? user.getId() : user.getHouseholdId();
  }
  public void requireCreator(String accountId) {
    if (!user(accountId).isHouseholdCreator()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household creator can manage family accounts.");
  }
}
