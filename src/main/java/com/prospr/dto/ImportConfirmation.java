package com.prospr.dto;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;
public record ImportConfirmation(String accountId, String filename, String checksum, String sourceType, String mapping, List<Row> rows) { public record Row(LocalDate date,String description,BigDecimal amount,Boolean income,String category,String memberId,String reference,Boolean include,Boolean duplicateAccepted,BigDecimal debitAmount,BigDecimal creditAmount,BigDecimal closingBalance) {} }
