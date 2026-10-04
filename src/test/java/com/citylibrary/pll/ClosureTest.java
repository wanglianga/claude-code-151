package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.repo.BookRepository;
import com.citylibrary.pll.repo.TransferRepository;
import com.citylibrary.pll.service.ClosureService;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 临时闭馆：拒绝新预约、取书期限顺延、闭馆馆不出库。 */
class ClosureTest extends AbstractIntegrationTest {

    @Autowired private ClosureService closureService;
    @Autowired private HoldService holdService;
    @Autowired private TransferService transferService;
    @Autowired private BookRepository bookRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private com.citylibrary.pll.support.TimeService timeService;

    @Test
    void closure_extendsPickupDeadlineAndBlocksPickupAndNewHolds() {
        Long santi = bookRepository.findByIsbn("9787536692930").orElseThrow().getId();
        Hold h = holdService.placeHold(santi, "P0001", "CENTRAL", null); // READY BC-1001
        var originalExpiry = holdService.get(h.getId()).getExpiresAt();

        // 中心馆闭馆 7 天：待取期限顺延
        closureService.close("CENTRAL", timeTodayPlus(7), "ADMIN01");
        Hold closedHold = holdService.get(h.getId());
        assertTrue(closedHold.getExpiresAt().isAfter(originalExpiry));

        // 超过原取书期限后，定时任务不应释放
        advanceDays(4);
        assertEquals(0, holdService.expireReadyHolds());
        assertEquals(HoldStatus.READY, holdService.get(h.getId()).getStatus());

        // 闭馆期间不能以该馆为取书馆预约
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> holdService.placeHold(santi, "P0002", "CENTRAL", null));
        assertEquals("R-TRANSFER-04", ex.getRuleRef());

        // 城东出库也被阻止（制造一条城东调拨）
        closureService.reopen("CENTRAL", "ADMIN01");
        // 重新闭城东前先构造调拨：P0003/P0004 约中心馆（第一本已锁 BC-1001），第二本从城东调拨
        holdService.placeHold(santi, "P0003", "CENTRAL", null);
        closureService.close("EAST", timeTodayPlus(5), "ADMIN01");
        Transfer t = transferRepository.findAll().stream()
                .filter(x -> x.getFromLibrary().getCode().equals("EAST")
                        && x.getStatus() == TransferStatus.CREATED)
                .findFirst().orElseThrow();
        BusinessRuleException pickupEx = assertThrows(BusinessRuleException.class,
                () -> transferService.pickup(t.getId(), "PKG-X-1", "COURIER01"));
        assertEquals("R-TRANSFER-04", pickupEx.getRuleRef());
    }

    private java.time.LocalDate timeTodayPlus(int days) {
        return timeService.today().plusDays(days);
    }
}
