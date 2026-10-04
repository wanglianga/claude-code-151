package com.example.library.web.dto;

import java.time.Instant;

public record ClaimView(
        Long id,
        Long loanId,
        String readerCardNo,
        String itemBarcode,
        String title,
        String status,
        String note,
        String staff,
        Instant createdAt,
        Instant resolvedAt
) {}
