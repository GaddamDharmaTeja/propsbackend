package com.prospr.service;

import com.prospr.model.TransactionEntry;

import java.util.List;
import java.util.Locale;

/** Seeded category master. Classification lives in data, not scattered if/else in the dashboard. */
public final class CategoryCatalog {

    public record Category(String code, String name, String classification) {}

    public static final List<Category> ALL = List.of(
            new Category("RENT", "Rent", "NECESSARY"),
            new Category("GROCERIES", "Groceries", "NECESSARY"),
            new Category("UTILITIES", "Bills & Utilities", "NECESSARY"),
            new Category("HEALTHCARE", "Healthcare", "NECESSARY"),
            new Category("TRANSPORT", "Transport", "NECESSARY"),
            new Category("EDUCATION", "Education", "NECESSARY"),
            new Category("INSURANCE", "Insurance", "NECESSARY"),
            new Category("DINING", "Dining", "DISCRETIONARY"),
            new Category("SHOPPING", "Shopping", "DISCRETIONARY"),
            new Category("ENTERTAINMENT", "Entertainment", "DISCRETIONARY"),
            new Category("SUBSCRIPTIONS", "Subscriptions", "DISCRETIONARY"),
            new Category("TRAVEL", "Travel", "DISCRETIONARY"),
            new Category("INCOME", "Income", "REST"),
            new Category("LOAN_EMI", "Loan / EMI", "REST"),
            new Category("OTHER", "Other", "MISCELLANEOUS")
    );

    private static final List<Rule> RULES = List.of(
            new Rule("bigbasket|blinkit|zepto|dmart|grocery|mart|supermarket", "Groceries"),
            new Rule("zomato|swiggy|restaurant|cafe|dominos|pizza|mcdonald|kfc", "Dining"),
            new Rule("uber|ola|rapido|petrol|diesel|metro|fuel", "Transport"),
            new Rule("amazon|flipkart|myntra|ajio", "Shopping"),
            new Rule("netflix|spotify|hotstar|prime video|youtube premium", "Subscriptions"),
            new Rule("electricity|water bill|broadband|airtel|jio|recharge", "Bills & Utilities"),
            new Rule("hospital|pharmacy|apollo|medical", "Healthcare"),
            new Rule("insurance|\\blic\\b", "Insurance"),
            new Rule("\\bemi\\b|loan repayment", "Loan / EMI"),
            new Rule("salary|payroll", "Income")
    );

    private CategoryCatalog() {}

    public static String typeOf(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return "MISCELLANEOUS";
        }
        String needle = categoryName.trim().toLowerCase(Locale.ROOT);
        for (Category category : ALL) {
            if (category.name().equalsIgnoreCase(categoryName) || category.code().equalsIgnoreCase(categoryName)) {
                return category.classification();
            }
        }
        if (needle.contains("dining") || needle.contains("shop") || needle.contains("entertain") || needle.contains("subscri") || needle.contains("travel")) {
            return "DISCRETIONARY";
        }
        if (needle.contains("income") || needle.contains("loan") || needle.contains("emi") || needle.contains("sip")) {
            return "REST";
        }
        if ("other".equals(needle) || needle.contains("misc")) {
            return "MISCELLANEOUS";
        }
        return "NECESSARY";
    }

    public static String matchName(String description) {
        if (description == null || description.isBlank()) {
            return "Other";
        }
        String value = description.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (value.matches(".*(" + rule.pattern + ").*")) {
                return rule.category;
            }
        }
        return "Other";
    }

    /** Fills category and system classification. Never overwrites a user override. */
    public static void apply(TransactionEntry transaction) {
        if (transaction == null) {
            return;
        }
        if (transaction.income) {
            if (transaction.category == null || transaction.category.isBlank() || "Other".equalsIgnoreCase(transaction.category)) {
                transaction.category = "Income";
            }
            transaction.systemClassification = "REST";
        } else {
            if (transaction.category == null || transaction.category.isBlank() || "Other".equalsIgnoreCase(transaction.category)) {
                transaction.category = matchName(transaction.description);
            }
            transaction.systemClassification = typeOf(transaction.category);
        }
        if (transaction.userClassification == null || transaction.userClassification.isBlank()) {
            transaction.classificationSource = "SYSTEM";
        }
    }

    public static boolean countsAsDiscretionary(TransactionEntry transaction) {
        if (transaction == null || transaction.income || transaction.excluded || transaction.internalTransfer) {
            return false;
        }
        String user = transaction.userClassification == null ? "" : transaction.userClassification.trim().toUpperCase(Locale.ROOT);
        if ("NECESSARY".equals(user) || "NOT_SURE".equals(user)) {
            return false;
        }
        if ("LIFESTYLE_CREEP".equals(user) || "DISCRETIONARY".equals(user)) {
            return true;
        }
        String system = transaction.systemClassification == null ? typeOf(transaction.category) : transaction.systemClassification;
        return "DISCRETIONARY".equalsIgnoreCase(system);
    }

    public static String effectiveLabel(TransactionEntry transaction) {
        if (transaction.userClassification != null && !transaction.userClassification.isBlank()) {
            return transaction.userClassification;
        }
        return transaction.systemClassification == null ? typeOf(transaction.category) : transaction.systemClassification;
    }

    private record Rule(String pattern, String category) {}
}
