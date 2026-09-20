package com.prospr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Proposed LCI from the lifestyle-creep design: unweighted average of positive discretionary increases. */
public final class LifestyleCreepMath {

    private LifestyleCreepMath() {}

    public static BigDecimal percentChange(BigDecimal current, BigDecimal baseline) {
        BigDecimal now = current == null ? BigDecimal.ZERO : current;
        BigDecimal base = baseline == null ? BigDecimal.ZERO : baseline;
        if (base.signum() == 0) {
            return now.signum() > 0 ? new BigDecimal("100.0") : new BigDecimal("0.0");
        }
        return now.subtract(base)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal index(List<BigDecimal> categoryIncreasePercents) {
        if (categoryIncreasePercents == null || categoryIncreasePercents.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : categoryIncreasePercents) {
            if (value != null && value.signum() > 0) {
                sum = sum.add(value);
            }
        }
        return sum.divide(BigDecimal.valueOf(categoryIncreasePercents.size()), 2, RoundingMode.HALF_UP);
    }

    public static String band(BigDecimal lci) {
        BigDecimal value = lci == null ? BigDecimal.ZERO : lci;
        if (value.compareTo(new BigDecimal("20")) < 0) {
            return "STABLE";
        }
        if (value.compareTo(new BigDecimal("50")) < 0) {
            return "MODERATE_CREEP";
        }
        return "SIGNIFICANT_CREEP";
    }

    public static BigDecimal monthlyFromDaily(BigDecimal spend, int usableDays) {
        if (usableDays <= 0 || spend == null) {
            return BigDecimal.ZERO;
        }
        return spend.multiply(BigDecimal.valueOf(30))
                .divide(BigDecimal.valueOf(usableDays), 2, RoundingMode.HALF_UP);
    }
}
