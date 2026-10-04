package com.citylibrary.pll.support;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 统一时钟：业务规则（到期日、超时释放、逾期天数）全部经此取时间，
 * 集成测试可注入固定时钟验证跨天场景。
 */
@Component
public class TimeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private Clock clock = Clock.system(ZONE);

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 测试专用：固定时钟 */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public void reset() {
        this.clock = Clock.system(ZONE);
    }
}
