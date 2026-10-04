package com.example.library.web.dto.admin;

import java.util.Map;

public record OverallDashboard(
        int windowDays,
        long transferTasks,
        long arrived,
        long lost,
        double arrivalRatePercent,
        long activeOverdueLoans,
        Map<String, Long> finesByType,
        double openFineTotal
) {}
