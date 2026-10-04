package com.example.library.web.dto;

import java.time.Instant;

public record TaskView(
        Long id,
        Long holdId,
        String itemBarcode,
        String title,
        String fromLibraryCode,
        String toLibraryCode,
        Long batchId,
        String packageNo,
        String status,
        boolean releaseOnArrival,
        Instant createdAt,
        Instant pickedAt,
        Instant eta,
        Instant arrivedAt
) {}
