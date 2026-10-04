package com.example.library.service;

import org.springframework.stereotype.Component;

import java.time.Instant;

/** 统一时钟，测试可固定/拨动时间以验证超时释放与逾期累计 */
@Component
public class AppClock {
    private volatile Instant fixed;

    public Instant now() {
        return fixed != null ? fixed : Instant.now();
    }

    public void setFixed(Instant t) {
        this.fixed = t;
    }

    public void reset() {
        this.fixed = null;
    }
}
