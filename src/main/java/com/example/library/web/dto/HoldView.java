package com.example.library.web.dto;

import java.time.Instant;

public record HoldView(
        Long id,
        Long bibId,
        String title,
        String readerCardNo,
        String pickupLibraryCode,
        String pickupLibraryName,
        Integer queuePosition,
        String status,
        String itemBarcode,
        Instant createdAt,
        Instant readyAt,
        Instant expiresAt,
        Instant cancelledAt
) {}
