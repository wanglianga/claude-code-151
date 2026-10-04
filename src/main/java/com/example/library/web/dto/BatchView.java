package com.example.library.web.dto;

import java.time.Instant;

public record BatchView(
        Long id,
        String batchNo,
        String staffName,
        String status,
        Instant createdAt,
        Instant closedAt
) {}
