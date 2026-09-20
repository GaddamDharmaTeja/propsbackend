package com.prospr.controller;
import com.prospr.model.*; import com.prospr.repository.*; import com.prospr.service.HouseholdAccess; import java.util.*; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") @CrossOrigin(origins="${PROSPR_WEB_ORIGIN:http://localhost:3000}") public class SettingsController {
 private final UserSettingsRepository settings; private final CategoryRuleRepository rules; private final HouseholdAccess access;
 public SettingsController(UserSettingsRepository s,CategoryRuleRepository r,HouseholdAccess access){settings=s;rules=r;this.access=access;}
 @GetMapping("/settings") public UserSettings get(@AuthenticationPrincipal String account){String household=access.householdId(account);return settings.findByHouseholdId(household).orElseGet(()->{UserSettings x=new UserSettings();x.ownerId=account;x.householdId=household;return settings.save(x);});}
 @PutMapping("/settings") public UserSettings put(@AuthenticationPrincipal String account,@RequestBody UserSettings item){String household=access.householdId(account);item.id=settings.findByHouseholdId(household).map(x->x.id).orElse(null);item.ownerId=account;item.householdId=household;return settings.save(item);}
 @GetMapping("/category-rules") public List<CategoryRule> rules(@AuthenticationPrincipal String account){return rules.findByHouseholdId(access.householdId(account));}
 @PostMapping("/category-rules") public CategoryRule addRule(@AuthenticationPrincipal String account,@RequestBody CategoryRule item){item.id=null;item.ownerId=account;item.householdId=access.householdId(account);return rules.save(item);}
}
