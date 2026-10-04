package com.example.library.web.dto;

import java.time.Instant;

public record LoanView(
        Long id,
        String readerCardNo,
        String readerName,
        String itemBarcode,
        String title,
        String checkoutLibraryCode,
        Instant checkedOutAt,
        Instant dueDate,
        int renewCount,
        String status,
        Instant returnedAt,
        String returnedLibraryCode,
        String conditionNote
) {}
