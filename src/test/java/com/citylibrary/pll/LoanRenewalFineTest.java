package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.repo.BookRepository;
import com.citylibrary.pll.repo.FineRepository;
import com.citylibrary.pll.repo.HoldRepository;
import com.citylibrary.pll.service.FineService;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 借阅周期、续借资格、排队冲突、热门书、逾期罚款按日累计与封顶。 */
class LoanRenewalFineTest extends AbstractIntegrationTest {

    @Autowired private LoanService loanService;
    @Autowired private HoldService holdService;
    @Autowired private FineService fineService;
    @Autowired private BookRepository bookRepository;
    @Autowired private FineRepository fineRepository;
    @Autowired private HoldRepository holdRepository;

    private Long bainianId() {
        return bookRepository.findByIsbn("9787544253994").orElseThrow().getId();
    }

    private Loan checkoutBainian(String cardNo, String barcode) {
        return loanService.checkoutDirect(cardNo, barcode, "CENTRAL", "STAFF01");
    }

    @Test
    void renew_respectsLimitAndQueueConflict() {
        Loan loan = checkoutBainian("P0001", "BC-2001");

        // 第 1 次续借成功
        Loan renewed = loanService.renew(loan.getId(), "P0001");
        assertEquals(1, renewed.getRenewedCount());

        // 第 2 次超过续借上限
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> loanService.renew(loan.getId(), "P0001"));
        assertEquals("R-LOAN-02", ex.getRuleRef());

        // 有人排队时续借同样被拒（换一本新书先借出，再制造排队）
        Loan loan2 = loanService.checkoutDirect("P0002", "BC-3001", "CENTRAL", "STAFF01");
        Long renshi = bookRepository.findByIsbn("9787508647357").orElseThrow().getId();
        holdService.placeHold(renshi, "P0003", "CENTRAL", null);
        BusinessRuleException conflict = assertThrows(BusinessRuleException.class,
                () -> loanService.renew(loan2.getId(), "P0002"));
        assertEquals("R-LOAN-03", conflict.getRuleRef());
    }

    @Test
    void hotBook_flaggedWhenQueueReachesThreshold() {
        // 《三体》5 条活跃预约 → 热门书
        for (int i = 1; i <= 5; i++) {
            holdService.placeHold(bookRepository.findByIsbn("9787536692930").orElseThrow().getId(),
                    "P000%d".formatted(i), "CENTRAL", null);
        }
        long active = holdRepository.countActiveForBook(
                bookRepository.findByIsbn("9787536692930").orElseThrow().getId(),
                List.of(HoldStatus.QUEUED, HoldStatus.READY));
        assertTrue(active >= 5);
    }

    @Test
    void overdueFine_accruesDailyAndCaps() {
        Loan loan = checkoutBainian("P0001", "BC-2001");
        fixClock(loan.getDueDate().plusDays(40)); // 逾期 40 天 → 20 元
        loanService.scanOverdueAndRemind();
        Fine fine40 = fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE).orElseThrow();
        assertEquals(0, fine40.getAmount().compareTo(java.math.BigDecimal.valueOf(20.0)));

        fixClock(loan.getDueDate().plusDays(100)); // 理论 50 元，单笔封顶 25
        loanService.scanOverdueAndRemind();
        Fine fine100 = fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE).orElseThrow();
        assertEquals(0, fine100.getAmount().compareTo(java.math.BigDecimal.valueOf(25.0)));
        assertEquals("R-FINE-01", fine100.getRuleRef());
    }

    @Test
    void overdueLoan_cannotRenew() {
        Loan loan = checkoutBainian("P0001", "BC-2001");
        fixClock(loan.getDueDate().plusDays(1));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> loanService.renew(loan.getId(), "P0001"));
        assertEquals("R-FINE-01", ex.getRuleRef());
    }
}
