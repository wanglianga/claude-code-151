package com.citylibrary.pll.dto;

import java.math.BigDecimal;
import java.util.List;

/** 搜索结果：本馆库存、跨馆可调库存、排队、预计到馆、电子资源。 */
public record CatalogView(
        Long bookId,
        String isbn,
        String title,
        String author,
        BigDecimal price,
        boolean ebookAvailable,
        String ebookUrl,
        long localAvailable,
        long queueLength,
        boolean hot,
        Integer minEtaDays,
        List<SourceStock> sources
) {
    public record SourceStock(
            Long libraryId,
            String libraryCode,
            String libraryName,
            boolean remote,
            long available,
            Integer etaDays,
            String note
    ) {}
}
