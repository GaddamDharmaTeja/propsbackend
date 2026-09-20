package com.prospr.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransaction(

        LocalDate date,

        String description,

        String referenceNumber,

        LocalDate valueDate,

        BigDecimal debit,

        BigDecimal credit,

        BigDecimal balance

) {
}