package com.prospr.controller;

import com.prospr.model.User;
import com.prospr.service.HouseholdAccess;
import com.prospr.service.LifestyleCreepService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/lifestyle-creep")
@CrossOrigin(origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}")
public class LifestyleCreepController {

    private final LifestyleCreepService creep;
    private final HouseholdAccess access;

    public LifestyleCreepController(LifestyleCreepService creep, HouseholdAccess access) {
        this.creep = creep;
        this.access = access;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@AuthenticationPrincipal String account, @RequestParam(required = false) String month, @RequestParam(defaultValue = "family") String view) {
        User user = access.user(account);
        boolean mine = "mine".equalsIgnoreCase(view);
        return creep.summary(access.householdId(account), parse(month), user.getMemberId(), user.isHouseholdCreator(), mine);
    }

    @GetMapping("/categories")
    public Map<String, Object> categories(@AuthenticationPrincipal String account, @RequestParam(required = false) String month) {
        return creep.categories(access.householdId(account), parse(month));
    }

    @PutMapping("/baselines")
    public Map<String, Object> saveBaseline(@AuthenticationPrincipal String account, @RequestBody Map<String, String> body) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(body.getOrDefault("monthlyAmount", "").trim());
        } catch (Exception ex) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a monthly baseline amount.");
        }
        return creep.saveBaseline(access.householdId(account), body.get("category"), amount);
    }

    @DeleteMapping("/baselines")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBaseline(@AuthenticationPrincipal String account, @RequestParam String category) {
        creep.deleteBaseline(access.householdId(account), category);
    }

    @GetMapping("/history")
    public Map<String, Object> history(@AuthenticationPrincipal String account, @RequestParam(defaultValue = "6") int months, @RequestParam(defaultValue = "family") String view) {
        User user = access.user(account);
        boolean mine = "mine".equalsIgnoreCase(view);
        return creep.history(access.householdId(account), months, user.getMemberId(), user.isHouseholdCreator(), mine);
    }

    private static YearMonth parse(String month) {
        return month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
    }
}
