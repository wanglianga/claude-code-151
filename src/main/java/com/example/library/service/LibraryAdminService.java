package com.example.library.service;

import com.example.library.domain.*;
import com.example.library.repo.HoldRepository;
import com.example.library.repo.LibraryRepository;
import com.example.library.repo.LoanRepository;
import com.example.library.web.ApiException;
import com.example.library.web.Views;
import com.example.library.web.dto.LibraryView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 馆内临时闭馆：闭馆期间自动顺延受影响的取书时限与应还日期（closure.graceHours），
 * 恢复开馆后不回收已顺延的时间，避免读者因闭馆被罚款。
 */
@Service
public class LibraryAdminService {

    private final LibraryRepository libraries;
    private final HoldRepository holds;
    private final LoanRepository loans;
    private final PolicyService policy;
    private final NotificationService notifications;
    private final AppClock clock;

    public LibraryAdminService(LibraryRepository libraries, HoldRepository holds, LoanRepository loans,
                               PolicyService policy, NotificationService notifications, AppClock clock) {
        this.libraries = libraries;
        this.holds = holds;
        this.loans = loans;
        this.policy = policy;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Library> list() {
        return libraries.findAll();
    }

    @Transactional
    public Library setTemporaryClosure(String code, boolean closed, String reason) {
        Library lib = libraries.lockById(libraries.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("分馆不存在: " + code)).getId()).orElseThrow();
        lib.setTemporaryClosed(closed);
        lib.setClosedUntil(closed ? clock.now().plus(policy.closureGraceHours(), ChronoUnit.HOURS) : null);
        long grace = policy.closureGraceHours();
        String detail = (reason == null || reason.isBlank() ? "" : "（原因：" + reason + "）");

        if (closed) {
            // 闭馆即时影响：在该馆待取的预约取书时限顺延并通知读者
            for (Hold h : holds.findActiveAtPickup(lib.getId(), List.of(HoldStatus.READY))) {
                if (h.getExpiresAt() != null) {
                    h.setExpiresAt(h.getExpiresAt().plus(grace, ChronoUnit.HOURS));
                    notifications.notify(h.getReader(), "HOLD_EXPIRED", lib.getName() + "临时闭馆通知",
                            "因" + lib.getName() + "临时闭馆" + detail + "，您《" + h.getBib().getTitle()
                                    + "》的取书时限顺延至 " + h.getExpiresAt() + "。");
                }
            }
            // 在该馆借出、且闭馆宽限内到期的借阅，应还日期顺延，避免无过错逾期
            Instant now = clock.now();
            for (Loan l : loans.findActiveAtCheckoutLibrary(lib.getId(), List.of(LoanStatus.ACTIVE))) {
                if (!l.getDueDate().isAfter(now.plus(grace, ChronoUnit.HOURS))) {
                    l.setDueDate(l.getDueDate().plus(grace, ChronoUnit.HOURS));
                }
            }
        }
        return lib;
    }

    @Transactional
    public Library reopen(String code) {
        Library lib = libraries.lockById(libraries.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("分馆不存在: " + code)).getId()).orElseThrow();
        lib.setTemporaryClosed(false);
        lib.setClosedUntil(null);
        return lib;
    }

    @Transactional(readOnly = true)
    public List<LibraryView> views() {
        return list().stream().map(Views::library).toList();
    }

    @Transactional
    public LibraryView setClosureView(String code, boolean closed, String reason) {
        return Views.library(setTemporaryClosure(code, closed, reason));
    }

    @Transactional
    public LibraryView reopenView(String code) {
        return Views.library(reopen(code));
    }
}
