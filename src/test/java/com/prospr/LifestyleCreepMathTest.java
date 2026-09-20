package com.prospr;

import com.prospr.service.LifestyleCreepMath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifestyleCreepMathTest {

    @Test
    void index_uses_only_positive_increases_and_counts_every_discretionary_category() {
        BigDecimal dining = LifestyleCreepMath.percentChange(new BigDecimal("9000"), new BigDecimal("6000"));
        BigDecimal shopping = LifestyleCreepMath.percentChange(new BigDecimal("13000"), new BigDecimal("9000"));
        BigDecimal entertainment = LifestyleCreepMath.percentChange(new BigDecimal("3500"), new BigDecimal("4000"));
        BigDecimal subscriptions = LifestyleCreepMath.percentChange(new BigDecimal("2500"), new BigDecimal("2000"));

        assertEquals(new BigDecimal("50.0"), dining);
        assertEquals(new BigDecimal("44.4"), shopping);
        assertEquals(new BigDecimal("-12.5"), entertainment);
        assertEquals(new BigDecimal("25.0"), subscriptions);
        assertEquals(new BigDecimal("29.85"), LifestyleCreepMath.index(List.of(dining, shopping, entertainment, subscriptions)));
        assertEquals("MODERATE_CREEP", LifestyleCreepMath.band(new BigDecimal("29.85")));
    }
}
