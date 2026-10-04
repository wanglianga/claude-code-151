package com.citylibrary.pll;

import com.citylibrary.pll.domain.CopyCondition;
import com.citylibrary.pll.domain.CopyStatus;
import com.citylibrary.pll.dto.StatsDtos;
import com.citylibrary.pll.repo.BookCopyRepository;
import com.citylibrary.pll.repo.BookRepository;
import com.citylibrary.pll.service.AdminService;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.LoanService;
import com.citylibrary.pll.service.ReturnService;
import com.citylibrary.pll.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** 管理端：馆/书目/读者/物流四视角统计、调拨效率（在途天数与准点率）、丢损。 */
class AdminStatsTest extends AbstractIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private HoldService holdService;
    @Autowired private LoanService loanService;
    @Autowired private TransferService transferService;
    @Autowired private ReturnService returnService;
    @Autowired private BookRepository bookRepository;
    @Autowired private BookCopyRepository copyRepository;

    @Test
    void bundle_reflectsFullWorkflow() {
        Long santi = bookRepository.findByIsbn("9787536692930").orElseThrow().getId();

        var h1 = holdService.placeHold(santi, "P0001", "CENTRAL", null);
        var loan = loanService.checkoutHold(h1.getId(), "BC-1001", "STAFF01");

        var h2 = holdService.placeHold(santi, "P0002", "CENTRAL", null);
        Long transferId = adminService.bundle().transfers().stream()
                .filter(t -> "BC-1002".equals(t.barcode()))
                .findFirst().orElseThrow().transferId();
        transferService.pickup(transferId, "PKG-S-1", "COURIER01");
        advanceDays(1);
        transferService.arrive(transferId, "STAFF02"); // SLA 2 天，实际 1 天 → 准点

        fixClock(loan.getDueDate().plusDays(6));
        loanService.scanOverdueAndRemind();

        StatsDtos.AdminBundle b = adminService.bundle();
        assertEquals(4, b.overview().libraries());
        // h1 已 FULFILLED，h2 到馆 READY → 活跃预约恰为 1
        assertEquals(1, b.overview().activeHolds());
        assertEquals(1, b.overview().readyHolds());
        assertEquals(1, b.overview().activeLoans());
        assertEquals(1, b.overview().overdueLoans());
        assertEquals(1, b.transfers().stream()
                .filter(t -> t.status().equals("ARRIVED")).count());
        assertTrue(b.overview().outstandingFines().signum() > 0);

        // 物流视角：准点且耗时 1 天
        StatsDtos.TransferStat ts = b.transfers().stream()
                .filter(t -> t.transferId().equals(transferId)).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, ts.onTime());
        assertEquals(1L, ts.transitDays());

        // 馆视角：城东出库、中心馆到馆且准点
        StatsDtos.LibraryStat east = b.byLibrary().stream()
                .filter(l -> l.code().equals("EAST")).findFirst().orElseThrow();
        assertTrue(east.transfersOut() >= 1);
        StatsDtos.LibraryStat central = b.byLibrary().stream()
                .filter(l -> l.code().equals("CENTRAL")).findFirst().orElseThrow();
        assertEquals(1, central.arrivedCount());
        assertEquals(1, central.onTimeCount());

        // 热门书目按活跃预约降序
        for (int i = 1; i < b.hotBooks().size(); i++) {
            assertTrue(b.hotBooks().get(i - 1).activeHolds() >= b.hotBooks().get(i).activeHolds());
        }

        // 审计轨迹存在（出库、到馆等）
        assertFalse(adminService.auditFor("TRANSFER", transferId).isEmpty());
    }

    @Test
    void damagedReturn_reflectedInStats() {
        loanService.checkoutDirect("P0001", "BC-2001", "CENTRAL", "STAFF01");
        returnService.returnBook("BC-2001", "CENTRAL", CopyCondition.DAMAGED,
                false, false, "书脊断裂", "STAFF01");
        StatsDtos.AdminBundle b = adminService.bundle();
        assertEquals(1, b.overview().damagedCopies());
        assertEquals(CopyStatus.DAMAGED,
                copyRepository.findByBarcode("BC-2001").orElseThrow().getStatus());
    }
}
