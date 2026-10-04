package com.citylibrary.pll.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时批处理：
 * - 每小时释放取书超时的预约给下一位；
 * - 每天 08:00 滚动逾期罚款与到期提醒。
 * 也可通过管理端接口手动触发，便于演示与补跑。
 */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final HoldService holdService;
    private final LoanService loanService;

    public ScheduledJobs(HoldService holdService, LoanService loanService) {
        this.holdService = holdService;
        this.loanService = loanService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void expireHolds() {
        int n = holdService.expireReadyHolds();
        if (n > 0) {
            log.info("超时取书释放预约 {} 条", n);
        }
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void scanOverdue() {
        int n = loanService.scanOverdueAndRemind();
        if (n > 0) {
            log.info("逾期/到期提醒处理 {} 条借阅", n);
        }
    }
}
