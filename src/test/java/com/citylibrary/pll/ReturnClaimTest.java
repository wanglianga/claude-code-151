package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.repo.FineRepository;
import com.citylibrary.pll.repo.LoanRepository;
import com.citylibrary.pll.service.IncidentService;
import com.citylibrary.pll.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** 读者声称已还但系统未入库：找到按申报日核销、争议期不滚罚款；确认遗失开赔偿单。 */
class ReturnClaimTest extends AbstractIntegrationTest {

    @Autowired private IncidentService incidentService;
    @Autowired private LoanService loanService;
    @Autowired private FineRepository fineRepository;
    @Autowired private LoanRepository loanRepository;

    @Test
    void claimFound_finesOnlyAccrueUntilClaimDate() {
        Loan loan = loanService.checkoutDirect("P0001", "BC-2001", "CENTRAL", "STAFF01");
        // 逾期 3 天时读者申报已还
        fixClock(loan.getDueDate().plusDays(3));
        Incident inc = incidentService.fileReturnClaim(loan.getId(), "记得已放入还书箱", "P0001");
        // 又过 20 天才在馆内找到
        fixClock(loan.getDueDate().plusDays(23));
        incidentService.resolveClaimFound(inc.getId(), "CENTRAL", "STAFF02");

        Loan closed = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.RETURNED, closed.getStatus());
        Fine fine = fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE).orElseThrow();
        // 仅计算申报前 3 天 = 1.5 元，而不是核查完成时的 23 天
        assertEquals(0, fine.getAmount().compareTo(java.math.BigDecimal.valueOf(1.5)));
    }

    @Test
    void claimLost_chargesBookPriceAndClosesLoan() {
        Loan loan = loanService.checkoutDirect("P0001", "BC-3001", "CENTRAL", "STAFF01");
        Incident inc = incidentService.fileReturnClaim(loan.getId(), "坚持称已归还", "P0001");
        fixClock(loan.getDueDate().plusDays(10));
        incidentService.resolveClaimLost(inc.getId(), "STAFF02");

        Loan closed = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.LOST, closed.getStatus());
        Fine comp = fineRepository.findByLoanIdAndType(loan.getId(), FineType.LOST_COMPENSATION)
                .orElseThrow();
        assertEquals(0, comp.getAmount().compareTo(java.math.BigDecimal.valueOf(68.0)));
        assertEquals("R-FINE-04", comp.getRuleRef());
    }
}
