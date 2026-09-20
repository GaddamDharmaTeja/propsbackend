package com.prospr.service;

import com.prospr.model.CreepBaseline;
import com.prospr.model.FamilyMember;
import com.prospr.model.TransactionEntry;
import com.prospr.repository.CreepBaselineRepository;
import com.prospr.repository.FamilyMemberRepository;
import com.prospr.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class LifestyleCreepService {

    private final TransactionRepository transactions;
    private final CreepBaselineRepository baselines;
    private final FamilyMemberRepository members;

    public LifestyleCreepService(TransactionRepository transactions, CreepBaselineRepository baselines, FamilyMemberRepository members) {
        this.transactions = transactions;
        this.baselines = baselines;
        this.members = members;
    }

    public Map<String, Object> summary(String householdId, YearMonth month) {
        return summary(householdId, month, null, false, false);
    }

    public Map<String, Object> summary(String householdId, YearMonth month, String memberId, boolean creator, boolean mineOnly) {
        YearMonth period = month == null ? YearMonth.now() : month;
        List<TransactionEntry> history = transactions.findByHouseholdIdAndDateBetweenAndExcludedFalse(
                householdId, LocalDate.of(2000, 1, 1), period.atEndOfMonth());
        return build(period, history, manualBaselines(householdId), householdId, memberId, creator, mineOnly);
    }

    public Map<String, Object> categories(String householdId, YearMonth month) {
        Map<String, Object> summary = summary(householdId, month);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", summary.get("month"));
        body.put("categories", summary.get("categories"));
        return body;
    }

    public Map<String, Object> history(String householdId, int months, String memberId, boolean creator, boolean mineOnly) {
        int span = Math.min(Math.max(months, 1), 12);
        YearMonth end = YearMonth.now();
        List<TransactionEntry> history = transactions.findByHouseholdIdAndDateBetweenAndExcludedFalse(
                householdId, end.minusMonths(span + 2L).atDay(1), end.atEndOfMonth());
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = span - 1; i >= 0; i--) {
            YearMonth period = end.minusMonths(i);
            Map<String, Object> point = build(period, history, manualBaselines(householdId), householdId, memberId, creator, mineOnly);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", point.get("month"));
            row.put("lci", point.get("lci"));
            row.put("status", point.get("status"));
            row.put("baselineSpend", point.get("baselineSpend"));
            row.put("currentSpend", point.get("currentSpend"));
            points.add(row);
        }
        return Map.of("months", points);
    }

    public List<String> insightLines(String householdId, YearMonth month) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) summary(householdId, month).get("categories");
        if (categories == null || categories.isEmpty()) {
            return List.of("Import at least two months of transactions to measure lifestyle creep.");
        }
        return categories.stream()
                .filter(row -> ((BigDecimal) row.get("increasePct")).signum() > 0)
                .sorted(Comparator.comparing((Map<String, Object> row) -> (BigDecimal) row.get("increasePct")).reversed())
                .limit(3)
                .map(row -> row.get("category") + " is " + row.get("increasePct") + "% above your baseline.")
                .toList();
    }

    public Map<String, Object> saveBaseline(String householdId, String category, BigDecimal amount) {
        String name = category == null ? "" : category.trim();
        if (name.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a category for this baseline.");
        }
        if (amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a monthly baseline of zero or more.");
        }
        CreepBaseline row = baselines.findByHouseholdIdAndCategoryIgnoreCase(householdId, name).orElseGet(CreepBaseline::new);
        row.householdId = householdId;
        row.category = name;
        row.monthlyAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        baselines.save(row);
        return summary(householdId, YearMonth.now());
    }

    public void deleteBaseline(String householdId, String category) {
        CreepBaseline row = baselines.findByHouseholdIdAndCategoryIgnoreCase(householdId, category == null ? "" : category.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Baseline not found."));
        baselines.delete(row);
    }

    private Map<String, BigDecimal> manualBaselines(String householdId) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (CreepBaseline row : baselines.findByHouseholdId(householdId)) {
            if (row.category != null && row.monthlyAmount != null) {
                amounts.put(row.category, row.monthlyAmount);
            }
        }
        return amounts;
    }

    private BigDecimal manualAmount(Map<String, BigDecimal> manual, String name) {
        for (Map.Entry<String, BigDecimal> entry : manual.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> build(YearMonth period, List<TransactionEntry> history, Map<String, BigDecimal> manual, String householdId, String memberId, boolean creator, boolean mineOnly) {
        LocalDate monthStart = period.atDay(1);
        Map<String, String> memberNames = new LinkedHashMap<>();
        String parentMemberId = null;
        for (FamilyMember member : members.findByHouseholdId(householdId)) {
            memberNames.put(member.id, member.name == null || member.name.isBlank() ? "Family" : member.name);
            if ("SELF".equalsIgnoreCase(member.relationship)) {
                parentMemberId = member.id;
            }
        }
        List<Map<String, Object>> contributions = new ArrayList<>();
        Set<LocalDate> usableDays = new TreeSet<>();
        Set<String> forcedDiscretionary = new HashSet<>();
        Map<String, BigDecimal> priorSpend = new LinkedHashMap<>();
        Map<String, BigDecimal> monthOne = new LinkedHashMap<>();
        Map<String, BigDecimal> monthTwo = new LinkedHashMap<>();
        Map<String, BigDecimal> current = new LinkedHashMap<>();
        YearMonth first = period.minusMonths(2);
        YearMonth second = period.minusMonths(1);

        for (TransactionEntry transaction : history) {
            if (mineOnly && !belongsToViewer(transaction, memberId, creator)) {
                continue;
            }
            if (transaction.userClassification != null && "LIFESTYLE_CREEP".equals(transaction.userClassification.toUpperCase(Locale.ROOT)) && transaction.category != null) {
                forcedDiscretionary.add(transaction.category);
            }
            if (!CategoryCatalog.countsAsDiscretionary(transaction) || transaction.date == null || transaction.date.isAfter(period.atEndOfMonth())) {
                continue;
            }
            String category = transaction.category == null || transaction.category.isBlank() ? "Other" : transaction.category;
            BigDecimal amount = transaction.amount == null ? BigDecimal.ZERO : transaction.amount.abs();
            if (transaction.date.isBefore(monthStart)) {
                usableDays.add(transaction.date);
                priorSpend.merge(category, amount, BigDecimal::add);
            }
            YearMonth when = YearMonth.from(transaction.date);
            if (when.equals(first)) {
                monthOne.merge(category, amount, BigDecimal::add);
            } else if (when.equals(second)) {
                monthTwo.merge(category, amount, BigDecimal::add);
            } else if (when.equals(period)) {
                current.merge(category, amount, BigDecimal::add);
                contributions.add(contribution(transaction, category, amount, parentMemberId, memberNames));
            }
        }

        boolean provisional = usableDays.size() < 60;
        Set<String> names = new TreeSet<>();
        names.addAll(monthOne.keySet());
        names.addAll(monthTwo.keySet());
        names.addAll(current.keySet());
        names.addAll(priorSpend.keySet());
        names.addAll(manual.keySet());
        names.removeIf(name -> !"DISCRETIONARY".equals(CategoryCatalog.typeOf(name)) && !forcedDiscretionary.contains(name) && manualAmount(manual, name) == null && current.getOrDefault(name, BigDecimal.ZERO).signum() == 0 && monthOne.getOrDefault(name, BigDecimal.ZERO).signum() == 0 && monthTwo.getOrDefault(name, BigDecimal.ZERO).signum() == 0);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<BigDecimal> increases = new ArrayList<>();
        BigDecimal baselineTotal = BigDecimal.ZERO;
        BigDecimal currentTotal = BigDecimal.ZERO;

        for (String name : names) {
            if (!"DISCRETIONARY".equals(CategoryCatalog.typeOf(name)) && !forcedDiscretionary.contains(name) && manualAmount(manual, name) == null) {
                continue;
            }
            BigDecimal entered = manualAmount(manual, name);
            BigDecimal baseline = entered != null
                    ? entered
                    : provisional
                    ? LifestyleCreepMath.monthlyFromDaily(priorSpend.getOrDefault(name, BigDecimal.ZERO), Math.max(usableDays.size(), 1))
                    : monthOne.getOrDefault(name, BigDecimal.ZERO).add(monthTwo.getOrDefault(name, BigDecimal.ZERO))
                    .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal now = current.getOrDefault(name, BigDecimal.ZERO);
            if (baseline.signum() == 0 && now.signum() == 0) {
                continue;
            }
            BigDecimal increase = LifestyleCreepMath.percentChange(now, baseline);
            increases.add(increase);
            baselineTotal = baselineTotal.add(baseline);
            currentTotal = currentTotal.add(now);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", name);
            row.put("baseline", baseline);
            row.put("baselineSource", entered != null ? "MANUAL" : (provisional ? "PROVISIONAL" : "HISTORY"));
            row.put("prior", priorSpend.getOrDefault(name, BigDecimal.ZERO));
            row.put("current", now);
            row.put("increasePct", increase);
            rows.add(row);
        }

        BigDecimal lci = LifestyleCreepMath.index(increases);
        String status = increases.isEmpty() ? "INSUFFICIENT_DATA" : (provisional ? "PROVISIONAL" : LifestyleCreepMath.band(lci));
        if (!increases.isEmpty() && provisional) {
            status = "PROVISIONAL";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", period.toString());
        body.put("usableDays", usableDays.size());
        boolean manualBaseline = rows.stream().anyMatch(row -> "MANUAL".equals(row.get("baselineSource")));
        body.put("baselineStatus", manualBaseline ? "MANUAL" : (provisional ? "PROVISIONAL" : "ESTABLISHED"));
        body.put("manualBaseline", manualBaseline);
        body.put("baselineSpend", baselineTotal);
        body.put("currentSpend", currentTotal);
        body.put("creepDelta", currentTotal.subtract(baselineTotal));
        body.put("lci", lci);
        body.put("status", increases.isEmpty() ? "INSUFFICIENT_DATA" : LifestyleCreepMath.band(lci));
        body.put("provisional", provisional);
        body.put("view", mineOnly ? "mine" : "family");
        body.put("contributions", contributions);
        body.put("categories", rows);
        body.put("statusNote", status);
        return body;
    }

    private boolean belongsToViewer(TransactionEntry transaction, String memberId, boolean creator) {
        if (memberId != null && memberId.equals(transaction.memberId)) {
            return true;
        }
        return creator && (transaction.memberId == null || transaction.memberId.isBlank());
    }

    private Map<String, Object> contribution(TransactionEntry transaction, String category, BigDecimal amount, String parentMemberId, Map<String, String> memberNames) {
        boolean parent = transaction.memberId == null || transaction.memberId.isBlank() || transaction.memberId.equals(parentMemberId);
        String who = parent ? "Parent" : memberNames.getOrDefault(transaction.memberId, "Family");
        String marked = transaction.userClassification == null ? "" : transaction.userClassification.toUpperCase(Locale.ROOT);
        String reason = "LIFESTYLE_CREEP".equals(marked)
                ? "You marked this payment as lifestyle creep."
                : category + " is a lifestyle category, so it is included in discretionary spend.";
        if (transaction.reasonHidden) {
            reason = "Anonymous";
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", transaction.date);
        row.put("description", transaction.reasonHidden ? "Anonymous" : transaction.description);
        row.put("category", category);
        row.put("amount", amount);
        row.put("who", who);
        row.put("parent", parent);
        row.put("reason", reason);
        return row;
    }
}
