package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.repo.BookRepository;
import com.citylibrary.pll.repo.FineRepository;
import com.citylibrary.pll.service.FineService;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.LoanService;
import com.citylibrary.pll.service.ReturnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** 欠款停借与馆员人工调整（减免/下调、必须留痕、不可擅自加罚）。 */
class FineBlockAndAdjustTest extends AbstractIntegrationTest {

    @Autowired private LoanService loanService;
    @Autowired private FineService fineService;
    @Autowired private HoldService holdService;
    @Autowired private ReturnService returnService;
    @Autowired private BookRepository bookRepository;
    @Autowired private FineRepository fineRepository;

    @Test
    void outstandingFines_blockBorrowAndHold_thenReleasedAfterPayment() {
        Long bainian = bookRepository.findByIsbn("9787544253994").orElseThrow().getId();
        Loan loan = loanService.checkoutDirect("P0001", "BC-2001", "CENTRAL", "STAFF01");
        fixClock(loan.getDueDate().plusDays(60)); // 罚款封顶 25 元 = 停借阈值
        loanService.scanOverdueAndRemind();

        BusinessRuleException block = assertThrows(BusinessRuleException.class,
                () -> holdService.placeHold(bainian, "P0001", "CENTRAL", null));
        assertEquals("R-FINE-07", block.getRuleRef());
        assertThrows(BusinessRuleException.class,
                () -> loanService.checkoutDirect("P0001", "BC-2002", "CENTRAL", "STAFF01"));

        Fine fine = fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE).orElseThrow();
        fineService.pay(fine.getId(), "STAFF01");
        // 结清后可再借
        assertDoesNotThrow(() -> loanService.checkoutDirect("P0001", "BC-2002", "CENTRAL", "STAFF01"));
    }

    @Test
    void manualAdjust_onlyReductionAndAudited() {
        Loan loan = loanService.checkoutDirect("P0001", "BC-2001", "CENTRAL", "STAFF01");
        fixClock(loan.getDueDate().plusDays(10)); // 5 元
        loanService.scanOverdueAndRemind();
        Fine fine = fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE).orElseThrow();

        // 调整必须登记馆员与原因
        assertThrows(IllegalArgumentException.class,
                () -> fineService.adjust(fine.getId(), new BigDecimal("1.00"), "读者申诉", null));
        // 不能加罚
        assertThrows(BusinessRuleException.class,
                () -> fineService.adjust(fine.getId(), new BigDecimal("9.00"), "x", "STAFF09"));

        Fine adjusted = fineService.adjust(fine.getId(), new BigDecimal("1.00"),
                "系统故障导致还书延迟，酌情减免", "STAFF09");
        assertEquals(FineStatus.ADJUSTED, adjusted.getStatus());
        assertTrue(adjusted.isManual());
        assertEquals("STAFF09", adjusted.getOperator());

        // 另开一张污损赔偿单，验证全额减免 → WAIVED 且金额清零
        loanService.checkoutDirect("P0002", "BC-2002", "CENTRAL", "STAFF01");
        Long damageFineId = returnService.returnBook("BC-2002", "CENTRAL",
                CopyCondition.DAMAGED, false, false, "轻微划痕", "STAFF01").fineIds().get(0);
        Fine waived = fineService.waive(damageFineId, "读者为志愿者，首次轻微污损免罚", "STAFF09");
        assertEquals(FineStatus.WAIVED, waived.getStatus());
        assertEquals(0, waived.getAmount().compareTo(BigDecimal.ZERO));
    }
}
