package com.example.library.web.dto;

import java.time.Instant;
import java.util.List;

public record CatalogView(
        Long bibId,
        String isbn,
        String title,
        String author,
        String subject,
        boolean ebookAvailable,
        String ebookUrl,
        String pickupLibraryCode,
        int localAvailable,
        long queueLength,
        Long estimatedArrivalHours,
        String suggestion,
        List<LibraryStock> libraries,
        Instant generatedAt
) {
    public record LibraryStock(
            String code,
            String name,
            String region,
            long totalCopies,
            long availableCopies,
            boolean transferable,
            Long etaHours,
            boolean temporaryClosed,
            double distanceKm
    ) {}
}
