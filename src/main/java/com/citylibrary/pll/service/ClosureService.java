package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.NotFoundException;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.HoldRepository;
import com.citylibrary.pll.repo.LibraryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 临时闭馆处理（R-TRANSFER-04）：
 * - 闭馆：停止新预约与出库；已到馆待取预约的取书期限顺延至复馆后；
 *   在途包裹仍可由接收入库，到馆通知与期限同样按复馆日起算；
 * - 复馆：恢复服务并通知受影响读者。
 */
@Service
public class ClosureService {

    private final LibraryRepository libraryRepository;
    private final HoldRepository holdRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final TimeService time;

    public ClosureService(LibraryRepository libraryRepository, HoldRepository holdRepository,
                          NotificationService notificationService, AuditService auditService,
                          RuleBookService ruleBook, TimeService time) {
        this.libraryRepository = libraryRepository;
        this.holdRepository = holdRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    @Transactional
    public Library close(String code, LocalDate reopenDate, String operator) {
        Library lib = libraryRepository.findLockedById(
                        libraryRepository.findByCode(code)
                                .orElseThrow(() -> new NotFoundException("馆代码不存在: " + code)).getId())
                .orElseThrow();
        if (reopenDate == null || !reopenDate.isAfter(time.today())) {
            throw new IllegalArgumentException("复馆日期必须晚于今天");
        }
        lib.setTempClosed(true);
        lib.setReopenDate(reopenDate);
        auditService.log("LIBRARY", lib.getId(), "TEMP_CLOSE",
                "临时闭馆，预计复馆 " + reopenDate, RuleBookService.R_CLOSURE, operator);

        int pickupDays = ruleBook.parameters().holdPickupDays();
        List<Hold> affected = holdRepository.findByStatusAndPickupLibraryId(
                HoldStatus.READY, lib.getId());
        for (Hold h : affected) {
            h.setExpiresAt(reopenDate.plusDays(pickupDays).atTime(23, 59)
                    .atOffset(time.now().getOffset()));
            notificationService.notify(h.getPatron(), NoticeType.CLOSURE_NOTICE,
                    "取书期限顺延通知",
                    lib.getName() + " 临时闭馆至 " + reopenDate
                            + "，您的取书期限顺延至 " + reopenDate.plusDays(pickupDays) + "。");
        }
        for (Hold h : holdRepository.findByStatusAndPickupLibraryId(HoldStatus.QUEUED, lib.getId())) {
            notificationService.notify(h.getPatron(), NoticeType.CLOSURE_NOTICE,
                    "取书馆临时闭馆通知",
                    lib.getName() + " 临时闭馆至 " + reopenDate
                            + "，您的预约保留，调拨与取书将在复馆后继续。");
        }
        return lib;
    }

    @Transactional
    public Library reopen(String code, String operator) {
        Library lib = libraryRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("馆代码不存在: " + code));
        if (!lib.isTempClosed()) {
            throw new IllegalStateException("该馆当前未闭馆");
        }
        lib.setTempClosed(false);
        lib.setReopenDate(null);
        auditService.log("LIBRARY", lib.getId(), "REOPEN", "恢复开放",
                RuleBookService.R_CLOSURE, operator);
        for (Hold h : holdRepository.findByStatusAndPickupLibraryId(HoldStatus.READY, lib.getId())) {
            notificationService.notify(h.getPatron(), NoticeType.CLOSURE_NOTICE,
                    "图书馆已恢复开放", lib.getName() + " 已恢复开放，请在取书期限前到馆取书。");
        }
        return lib;
    }
}
