package com.example.library.service;

import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import com.example.library.repo.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 定时批处理：取书超时释放、逾期罚款累计、到期提醒。
 * 可通过 app.scheduling.enabled=false 关闭（测试中手动触发对应服务方法）。
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class LibraryScheduler {

    private static final Logger log = LoggerFactory.getLogger(LibraryScheduler.class);

    private final HoldService holdService;
    private final FineService fineService;
    private final LoanRepository loans;
    private final NotificationService notifications;
    private final AppClock clock;

    public LibraryScheduler(HoldService holdService, FineService fineService, LoanRepository loans,
                            NotificationService notifications, AppClock clock) {
        this.holdService = holdService;
        this.fineService = fineService;
        this.loans = loans;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** 每小时：到馆未取超时释放给下一位 */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 5_000L)
    @Transactional
    public void expireHolds() {
        int n = holdService.expireOverdueHolds();
        if (n > 0) {
            log.info("取书超时检查：处理 {} 条预约", n);
        }
    }

    /** 每天 02:00：逾期罚款刷新 + 到期前 1 天提醒（声称已还争议中的借阅暂停） */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void dailyOverdue() {
        List<Loan> active = loans.findByStatusIn(List.of(LoanStatus.ACTIVE));
        for (Loan l : active) {
            String barcode = l.getItem().getBarcode();
            if (l.getDueDate().isBefore(clock.now())) {
                fineService.refreshOverdueFine(l);
                if (!notifications.alreadySentWith(l.getReader().getId(), "OVERDUE", barcode)) {
                    notifications.notify(l.getReader(), "OVERDUE", "图书逾期提醒",
                            "您借阅的《" + l.getItem().getBib().getTitle() + "》（条码 " + barcode
                                    + "）已超过应还日期 " + l.getDueDate() + "，请尽快归还，罚款按日累计。");
                }
            } else if (!l.getDueDate().isAfter(clock.now().plus(1, ChronoUnit.DAYS))
                    && !notifications.alreadySentWith(l.getReader().getId(), "DUE_REMINDER", barcode)) {
                notifications.notify(l.getReader(), "DUE_REMINDER", "图书即将到期",
                        "您借阅的《" + l.getItem().getBib().getTitle() + "》（条码 " + barcode
                                + "）将于 " + l.getDueDate() + " 到期，可在无排队冲突时续借。");
            }
        }
    }
}
