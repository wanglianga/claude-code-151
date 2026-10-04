package com.example.library.web.dto.admin;

import java.time.Instant;

public record BatchStatsRow(
        Long batchId,
        String batchNo,
        String staffName,
        String status,
        Instant createdAt,
        Instant closedAt,
        long packages,
        long arrived,
        long lost,
        long inProgress,
        double avgTransitHours
) {}
