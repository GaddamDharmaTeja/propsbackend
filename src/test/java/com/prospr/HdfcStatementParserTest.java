package com.prospr;

import static org.junit.jupiter.api.Assertions.*;

import com.prospr.controller.ImportController;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the real HDFC layout. It is opt-in so no statement or
 * password is committed: set HDFC_STATEMENT_PATH and HDFC_STATEMENT_PASSWORD.
 */
class HdfcStatementParserTest {
  @SuppressWarnings("unchecked")
  @Test
  void amanUsesDebitAmountNotClosingBalanceOrFooter() throws Exception {
    String path = System.getenv("HDFC_STATEMENT_PATH");
    String password = System.getenv("HDFC_STATEMENT_PASSWORD");
    Assumptions.assumeTrue(path != null && password != null && Files.exists(Path.of(path)), "HDFC fixture not configured");

    ImportController controller = new ImportController(null, null, null, null, null, null, null);
    Method pdf = ImportController.class.getDeclaredMethod("pdf", byte[].class, String.class);
    pdf.setAccessible(true);
    List<List<String>> rows = (List<List<String>>) pdf.invoke(controller, Files.readAllBytes(Path.of(path)), password);

    List<String> aman = rows.stream().filter(row -> row.size() > 1 && row.get(1).contains("UPI-AMAN")).findFirst().orElseThrow();
    assertEquals("02/09/26", aman.get(0));
    assertEquals("606.00", aman.get(4));
    assertEquals("", aman.get(5));
    assertEquals("31785.95", aman.get(6));
    assertFalse(aman.get(1).contains("HDFC BANK LIMITED"));
    assertFalse(aman.get(1).contains("560047"));
    assertFalse(aman.get(1).contains("560,047"));

    Method preview = ImportController.class.getDeclaredMethod("toPreview", List.class, List.class, String.class, List.class);
    preview.setAccessible(true);
    Map<String, Object> parsed = (Map<String, Object>) preview.invoke(controller, aman, rows.get(0), "account", List.of());
    assertEquals(LocalDate.of(2026, 9, 2), parsed.get("date"));
    assertEquals(new BigDecimal("606.00"), parsed.get("amount"));
    assertEquals(new BigDecimal("31785.95"), parsed.get("closingBalance"));
    assertEquals(new BigDecimal("606.00"), parsed.get("debitAmount"));
    assertNull(parsed.get("creditAmount"));
    assertEquals(false, parsed.get("income"));
    assertEquals(false, parsed.get("potentialDuplicate"));
    assertFalse(String.valueOf(parsed.get("description")).contains("HDFC BANK LIMITED"));

    List<String> fuel = rows.stream().filter(row -> row.size() > 1 && row.get(1).contains("V I ENTERPRISES")).findFirst().orElseThrow();
    Map<String, Object> categorized = (Map<String, Object>) preview.invoke(controller, fuel, rows.get(0), "account", List.of());
    assertEquals("Transport", categorized.get("category"));
  }
}
