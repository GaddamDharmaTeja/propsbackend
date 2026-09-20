package com.prospr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.prospr.controller.ImportController;
import com.prospr.dto.ImportConfirmation;
import com.prospr.model.FinancialAccount;
import com.prospr.model.ImportBatch;
import com.prospr.model.TransactionEntry;
import com.prospr.repository.*;
import com.prospr.service.HouseholdAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportConfirmationTest {
  @Test
  void savesAmanAsSingleDebitAndSkipsReimport() {
    TransactionRepository transactions=mock(TransactionRepository.class);
    ImportBatchRepository batches=mock(ImportBatchRepository.class);
    FinancialAccountRepository accounts=mock(FinancialAccountRepository.class);
    HouseholdAccess access=mock(HouseholdAccess.class);
    ImportController controller=new ImportController(transactions,batches,accounts,mock(CategoryRuleRepository.class),access,null,null);
    FinancialAccount bank=new FinancialAccount(); bank.id="bank"; bank.householdId="home";
    when(access.householdId("member")).thenReturn("home");
    when(accounts.findById("bank")).thenReturn(Optional.of(bank));
    when(transactions.findByHouseholdIdOrderByDateDesc("home")).thenReturn(List.of());
    when(batches.save(any())).thenAnswer(call -> { ImportBatch batch=call.getArgument(0); if(batch.id==null) batch.id="batch"; return batch; });
    ImportConfirmation payload=new ImportConfirmation("bank","statement.pdf","checksum","PDF","HDFC columns",
      List.of(new ImportConfirmation.Row(LocalDate.of(2026,9,2),"UPI-AMAN VERMA-9340108718@YESCRED",new BigDecimal("606.00"),false,"Other",null,"0000101934809087",true,false,new BigDecimal("606.00"),null,new BigDecimal("31785.95"))));

    ImportBatch response=controller.confirm("member",payload).getBody();
    ArgumentCaptor<List<TransactionEntry>> saved=ArgumentCaptor.forClass(List.class);
    verify(transactions).saveAll(saved.capture());
    TransactionEntry aman=saved.getValue().get(0);
    assertEquals(new BigDecimal("606.00"),aman.amount);
    assertFalse(aman.income);
    assertEquals(new BigDecimal("606.00"), aman.debitAmount);
    assertNull(aman.creditAmount);
    assertEquals(new BigDecimal("31785.95"), aman.closingBalance);
    assertEquals("UPI-AMAN VERMA-9340108718@YESCRED",aman.description);
    assertEquals("0000101934809087",aman.reference);
    assertEquals("home",aman.householdId);
    assertEquals(1,response.importedCount);
    assertEquals(0,response.skippedCount);

    when(transactions.findByHouseholdIdOrderByDateDesc("home")).thenReturn(List.of(aman));
    ImportBatch repeat=controller.confirm("member",payload).getBody();
    verify(transactions,times(1)).saveAll(anyList());
    assertEquals(0,repeat.importedCount);
    assertEquals(1,repeat.skippedCount);
  }
}
