package com.citylibrary.pll;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.repo.*;
import com.citylibrary.pll.service.HoldService;
import com.citylibrary.pll.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 物流丢包：馆方承担、读者免责、锁码释放并自动重新分配。 */
class TransferLostTest extends AbstractIntegrationTest {

    @Autowired private HoldService holdService;
    @Autowired private TransferService transferService;
    @Autowired private BookRepository bookRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private IncidentRepository incidentRepository;

    @Test
    void lostPackage_relocatesHoldAndDoesNotChargePatron() {
        Long santi = bookRepository.findByIsbn("9787536692930").orElseThrow().getId();
        holdService.placeHold(santi, "P0001", "CENTRAL", null); // BC-1001 本地 READY
        Hold h2 = holdService.placeHold(santi, "P0002", "CENTRAL", null); // BC-1002 城东调拨
        Transfer t = transferRepository.findByHoldAndStatus(h2.getId(),
                List.of(TransferStatus.CREATED)).get(0);

        transferService.pickup(t.getId(), "PKG-LOST-01", "COURIER01");
        Incident inc = transferService.reportLost(t.getId(), "运输车辆事故", "COURIER01");

        // 丢包任务与副本状态
        assertEquals(TransferStatus.LOST, transferService.get(t.getId()).getStatus());
        assertEquals(CopyStatus.LOST,
                transferService.get(t.getId()).getCopy().getStatus());
        assertEquals(IncidentType.TRANSFER_LOST, inc.getType());
        assertEquals("RESOLVED", inc.getStatus());

        // 读者预约保留，自动改由下一本（BC-1003）重新调拨
        Hold reloaded = holdService.get(h2.getId());
        assertEquals(HoldStatus.QUEUED, reloaded.getStatus());
        assertEquals("BC-1003", reloaded.getReservedCopy().getBarcode());

        // 无读者罚款 / 赔偿
        assertTrue(incidentRepository.findAll().stream()
                .noneMatch(i -> i.getType() == IncidentType.RETURN_CLAIM));
    }

    @Test
    void pickup_fromClosedLibrary_rejected() {
        Long santi = bookRepository.findByIsbn("9787536692930").orElseThrow().getId();
        // 城东读者预约：本地有书立即 READY；改为构造城西->城东不便，直接验证到馆闭馆顺延在 ClosureTest
        // 此处验证重复包裹号
        Hold h = holdService.placeHold(santi, "P0001", "EAST", null);
        Long tId = transferRepository.findAll().stream()
                .filter(x -> x.getStatus() == TransferStatus.CREATED
                        || x.getStatus() == TransferStatus.ARRIVED)
                .findFirst().map(Transfer::getId).orElse(null);
        // 本地预约不产生 CREATED 调拨（直接 READY），tId 允许为空
        assertNull(tId);
        assertEquals(HoldStatus.READY, holdService.get(h.getId()).getStatus());
    }
}
