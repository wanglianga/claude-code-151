package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.repo.*;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.LoanService;
import com.citylibrary.pll.service.ReturnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** 还书核验：逾期、污损/缺页赔偿、重复入库防护、跨馆还书补给排队。 */
class ReturnCheckTest extends AbstractIntegrationTest {

    @Autowired private ReturnService returnService;
    @Autowired private LoanService loanService;
    @Autowired private HoldService holdService;
    @Autowired private BookRepository bookRepository;
    @Autowired private FineRepository fineRepository;
    @Autowired private BookCopyRepository copyRepository;

    @Test
    void returnWithDamage_chargesAndShelvesDamaged() {
        Loan loan = loanService.checkoutDirect("P0001", "BC-2001", "CENTRAL", "STAFF01");
        ReturnService.ReturnResult r = returnService.returnBook(
                "BC-2001", "CENTRAL", CopyCondition.DAMAGED, false, false, "封面水渍", "STAFF01");
        assertFalse(r.overdue());
        assertEquals("DAMAGED", r.copyStatus());
        assertEquals(1, r.fineIds().size());
        Fine fine = fineRepository.findById(r.fineIds().get(0)).orElseThrow();
        assertEquals(FineType.DAMAGE, fine.getType());
        assertEquals(0, fine.getAmount().compareTo(java.math.BigDecimal.TEN));
        assertEquals(CopyStatus.DAMAGED, copyRepository.findByBarcode("BC-2001").orElseThrow().getStatus());
    }

    @Test
    void returnWithMissingPages_severeCharge() {
        Loan loan = loanService.checkoutDirect("P0001", "BC-2002", "CENTRAL", "STAFF01");
        ReturnService.ReturnResult r = returnService.returnBook(
                "BC-2002", "CENTRAL", CopyCondition.GOOD, true, true, "读者归还", "STAFF01");
        Fine fine = fineRepository.findById(r.fineIds().get(0)).orElseThrow();
        assertEquals(FineType.SEVERE_DAMAGE, fine.getType());
        assertEquals(0, fine.getAmount().compareTo(java.math.BigDecimal.valueOf(30)));
        assertEquals(CopyCondition.SEVERE,
                copyRepository.findByBarcode("BC-2002").orElseThrow().getConditionState());
    }

    @Test
    void doubleReturn_rejected() {
        loanService.checkoutDirect("P0001", "BC-2003", "CENTRAL", "STAFF01");
        returnService.returnBook("BC-2003", "CENTRAL", CopyCondition.GOOD,
                false, false, null, "STAFF01");
        assertThrows(IllegalStateException.class, () -> returnService.returnBook(
                "BC-2003", "CENTRAL", CopyCondition.GOOD, false, false, null, "STAFF01"));
    }

    @Test
    void crossLibraryReturn_fillsQueueAtReturnLibrary() {
        Long santi = bookRepository.findByIsbn("9787536692930").orElseThrow().getId();
        // P0001 中心馆取本馆 BC-1001 借出
        Hold h = holdService.placeHold(santi, "P0001", "CENTRAL", null);
        Loan loan = loanService.checkoutHold(h.getId(), "BC-1001", "STAFF01");
        // 在城东馆归还：BC-1001 落在城东
        returnService.returnBook("BC-1001", "EAST", CopyCondition.GOOD,
                false, false, null, "STAFF01");
        assertEquals("EAST", copyRepository.findByBarcode("BC-1001").orElseThrow()
                .getCurrentLibrary().getCode());
        // 城东读者预约：立即由这本跨馆还入的副本在本馆满足
        Hold h2 = holdService.placeHold(santi, "P0002", "EAST", null);
        assertEquals(HoldStatus.READY, holdService.get(h2.getId()).getStatus());
        assertEquals("BC-1001", holdService.get(h2.getId()).getReservedCopy().getBarcode());
    }

    @Test
    void normalReturn_accruesOverdueFine() {
        Loan loan = loanService.checkoutDirect("P0001", "BC-2001", "CENTRAL", "STAFF01");
        fixClock(loan.getDueDate().plusDays(5));
        ReturnService.ReturnResult r = returnService.returnBook(
                "BC-2001", "CENTRAL", CopyCondition.GOOD, false, false, null, "STAFF01");
        assertTrue(r.overdue());
        Fine fine = fineRepository.findById(r.fineIds().get(0)).orElseThrow();
        assertEquals(0, fine.getAmount().compareTo(java.math.BigDecimal.valueOf(2.5)));
    }
}
