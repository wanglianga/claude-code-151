package com.citylibrary.pll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 规则手册参数。规则同时照顾读者体验、馆藏流转与物流成本，
 * 馆员可通过 GET /api/rules 查看依据；所有人工调整都会记录 rule_ref。
 */
@ConfigurationProperties(prefix = "app.rules")
public record RulesProperties(
        int loanPeriodDays,
        int renewLimit,
        int holdPickupDays,
        BigDecimal overdueDailyFine,
        BigDecimal overdueCapPerLoan,
        BigDecimal damageFine,
        BigDecimal severeDamageFine,
        double localRetainRatio,
        double remoteRetainRatio,
        int normalSlaDays,
        int remoteSlaDays,
        int hotHoldThreshold,
        BigDecimal blockOutstandingAmount
) {}
