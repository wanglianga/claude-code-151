package com.example.library.web.dto.admin;

import java.util.Map;

public record BibStats(
        Long bibId,
        String isbn,
        String title,
        String author,
        long totalCopies,
        Map<String, Long> copiesByStatus,
        long activeHolds,
        long activeLoans,
        boolean ebookAvailable
) {}
