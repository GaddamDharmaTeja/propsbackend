package com.prospr.controller;

import com.prospr.dto.FamilyMemberResponse;
import com.prospr.model.FamilyMember;
import com.prospr.model.Goal;
import com.prospr.model.TransactionEntry;
import com.prospr.repository.FamilyMemberRepository;
import com.prospr.repository.GoalRepository;
import com.prospr.repository.TransactionRepository;
import com.prospr.service.HouseholdAccess;
import com.prospr.service.LifestyleCreepService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "${PROSPR_WEB_ORIGIN:http://localhost:3000}")
public class DashboardController {

    private final TransactionRepository transactions;
    private final GoalRepository goals;
    private final FamilyMemberRepository family;
    private final HouseholdAccess access;
    private final LifestyleCreepService creep;

    public DashboardController(TransactionRepository transactions, GoalRepository goals, FamilyMemberRepository family, HouseholdAccess access, LifestyleCreepService creep) {
        this.transactions = transactions;
        this.goals = goals;
        this.family = family;
        this.access = access;
        this.creep = creep;
    }

    @GetMapping
    public Map<String, Object> dashboard(@AuthenticationPrincipal String account, @RequestParam(required = false) String month, @RequestParam(defaultValue = "family") String view) {
        String household = access.householdId(account);
        boolean mine = "mine".equalsIgnoreCase(view);
        YearMonth period = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        YearMonth previous = period.minusMonths(1);
        List<TransactionEntry> rows = filterView(spendRows(household, period), account, mine);
        List<TransactionEntry> prior = filterView(spendRows(household, previous), account, mine);
        BigDecimal income = sum(rows, true);
        BigDecimal spend = sum(rows, false);
        BigDecimal priorIncome = sum(prior, true);
        BigDecimal priorSpend = sum(prior, false);
        BigDecimal savings = income.subtract(spend);
        BigDecimal priorSavings = priorIncome.subtract(priorSpend);
        Map<String, BigDecimal> categories = rows.stream()
                .filter(row -> !row.income)
                .collect(Collectors.groupingBy(row -> row.category == null ? "Other" : row.category,
                        Collectors.reducing(BigDecimal.ZERO, row -> row.amount.abs(), BigDecimal::add)));
        List<FamilyMember> members = family.findByHouseholdId(household);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", period.toString());
        result.put("income", income);
        result.put("spending", spend);
        result.put("savings", savings);
        result.put("incomeChangePct", change(income, priorIncome));
        result.put("spendingChangePct", change(spend, priorSpend));
        result.put("savingsChangePct", change(savings, priorSavings));
        result.put("categoryBreakdown", categories);
        result.put("memberSpend", memberSpend(rows, members));
        result.put("recentTransactions", rows.stream().sorted(Comparator.comparing((TransactionEntry row) -> row.date).reversed()).limit(5).toList());
        result.put("familyMembers", members.stream().map(FamilyMemberResponse::roster).toList());
        result.put("goals", goals.findByHouseholdId(household));
        result.put("view", mine ? "mine" : "family");
        result.put("insights", creep.insightLines(household, period));
        return result;
    }

    private static List<TransactionEntry> filterView(List<TransactionEntry> rows, String account, boolean mine) {
        if (!mine) {
            return rows;
        }
        return rows.stream().filter(row -> account.equals(row.ownerId) || account.equals(row.updatedBy)).toList();
    }

    private List<TransactionEntry> spendRows(String household, YearMonth period) {
        return transactions.findByHouseholdIdAndDateBetweenAndExcludedFalse(household, period.atDay(1), period.atEndOfMonth()).stream()
                .filter(row -> !row.internalTransfer)
                .toList();
    }

    private static BigDecimal sum(List<TransactionEntry> rows, boolean income) {
        return rows.stream().filter(row -> row.income == income).map(row -> row.amount.abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO : new BigDecimal("100");
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 0, RoundingMode.HALF_UP);
    }

    private static List<Map<String, Object>> memberSpend(List<TransactionEntry> rows, List<FamilyMember> members) {
        Map<String, String> names = new LinkedHashMap<>();
        for (FamilyMember member : members) {
            names.put(member.id, member.name);
            if (member.accountId != null) {
                names.put(member.accountId, member.name);
            }
        }
        return rows.stream()
                .filter(row -> !row.income)
                .collect(Collectors.groupingBy(row -> {
                    if (row.memberId != null && names.containsKey(row.memberId)) return names.get(row.memberId);
                    return "Household";
                }, Collectors.reducing(BigDecimal.ZERO, row -> row.amount.abs(), BigDecimal::add)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.getKey());
                    row.put("amount", entry.getValue());
                    return row;
                })
                .toList();
    }
}
