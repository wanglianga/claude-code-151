package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.repo.*;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.LoanService;
import com.citylibrary.pll.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** 预约 → 锁码 → 调拨 → 到馆通知 → 取书 → 超时释放给下一位 全链路。 */
class HoldFlowTest extends AbstractIntegrationTest {

    @Autowired private HoldService holdService;
    @Autowired private LoanService loanService;
    @Autowired private TransferService transferService;
    @Autowired private BookRepository bookRepository;
    @Autowired private BookCopyRepository copyRepository;
    @Autowired private TransferRepository transferRepository;

    private Long santiId() {
        return bookRepository.findByIsbn("9787536692930").orElseThrow().getId();
    }

    @Test
    void fullPipeline_localThenTransfer_thenPickupAndExpiryHandsOff() {
        Long bookId = santiId();

        // 1. P0001 中心馆预约：本馆有书，立即 READY 并锁定 BC-1001
        Hold h1 = holdService.placeHold(bookId, "P0001", "CENTRAL", "STAFF01");
        h1 = holdService.get(h1.getId());
        assertEquals(HoldStatus.READY, h1.getStatus());
        assertEquals("BC-1001", h1.getReservedCopy().getBarcode());
        assertNotNull(h1.getExpiresAt());

        // 2. P0002 再约：本馆无书，排队并从城东生成调拨任务
        Hold h2 = holdService.placeHold(bookId, "P0002", "CENTRAL", "STAFF01");
        h2 = holdService.get(h2.getId());
        assertEquals(HoldStatus.QUEUED, h2.getStatus());
        assertEquals("BC-1002", h2.getReservedCopy().getBarcode());
        Transfer t = transferRepository.findByHoldAndStatus(h2.getId(),
                java.util.List.of(TransferStatus.CREATED, TransferStatus.PICKED_UP)).get(0);
        assertEquals("EAST", t.getFromLibrary().getCode());
        assertNotNull(t.getEtaDate());

        // 3. 物流员取书出库（包裹编号）→ 到馆 → 读者收到取书通知（READY）
        transferService.pickup(t.getId(), "PKG-20261003-01", "COURIER01");
        transferService.arrive(t.getId(), "STAFF02");
        h2 = holdService.get(h2.getId());
        assertEquals(HoldStatus.READY, h2.getStatus());

        // 4. P0001 取书借出
        Loan loan = loanService.checkoutHold(h1.getId(), "BC-1001", "STAFF02");
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        assertEquals(CopyStatus.ON_LOAN, loan.getCopy().getStatus());
        assertEquals(HoldStatus.FULFILLED, holdService.get(h1.getId()).getStatus());

        // 5. P0003 排队：等待 P0002 超时后接棒
        Hold h3 = holdService.placeHold(bookId, "P0003", "CENTRAL", "STAFF01");
        assertEquals(HoldStatus.QUEUED, holdService.get(h3.getId()).getStatus());

        // 6. 超过取书期限：P0002 释放，BC-1002 直接在中心馆补给 P0003
        advanceDays(4);
        int expired = holdService.expireReadyHolds();
        assertEquals(1, expired);
        assertEquals(HoldStatus.EXPIRED, holdService.get(h2.getId()).getStatus());
        h3 = holdService.get(h3.getId());
        assertEquals(HoldStatus.READY, h3.getStatus());
        assertEquals("BC-1002", h3.getReservedCopy().getBarcode());
    }

    @Test
    void duplicateHold_rejected() {
        Long bookId = santiId();
        holdService.placeHold(bookId, "P0001", "CENTRAL", null);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> holdService.placeHold(bookId, "P0001", "CENTRAL", null));
        assertEquals("R-HOLD-01", ex.getRuleRef());
    }

    @Test
    void cancelBeforePickup_cancelsTransferAndShelvesCopy() {
        Long bookId = santiId();
        holdService.placeHold(bookId, "P0001", "CENTRAL", null); // 吃掉本馆 BC-1001
        Hold h2 = holdService.placeHold(bookId, "P0002", "CENTRAL", null); // 城东调拨
        assertEquals(HoldStatus.QUEUED, holdService.get(h2.getId()).getStatus());

        holdService.cancel(h2.getId(), "不想要了", null);
        assertEquals(HoldStatus.CANCELLED, holdService.get(h2.getId()).getStatus());
        Transfer byCopy = transferRepository.findAll().stream()
                .filter(x -> x.getCopy().getBarcode().equals("BC-1002")).findFirst().orElseThrow();
        assertEquals(TransferStatus.CANCELLED, byCopy.getStatus());
        assertEquals(CopyStatus.AVAILABLE,
                copyRepository.findByBarcode("BC-1002").orElseThrow().getStatus());
    }
}
