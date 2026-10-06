package com.prospr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.prospr.service.CategoryCatalog;
import org.junit.jupiter.api.Test;

class CategoryTypeTest {
  @Test
  void builtInIncomeIsCreditAndRentIsDebit() {
    assertEquals("CREDIT", CategoryCatalog.transactionTypeOf("Income"));
    assertEquals("DEBIT", CategoryCatalog.transactionTypeOf("Rent"));
  }
}
