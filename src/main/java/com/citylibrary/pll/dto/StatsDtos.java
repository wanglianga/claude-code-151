package com.citylibrary.pll.dto;

import java.math.BigDecimal;
import java.util.List;

public class StatsDtos {

    public record Overview(
            long libraries,
            long books,
            long copies,
            long availableCopies,
            long activeHolds,
            long readyHolds,
            long activeLoans,
            long overdueLoans,
            long transfersInProgress,
            long transfersLost,
            long damagedCopies,
            long lostCopies,
            BigDecimal outstandingFines
    ) {}

    /** 按馆：库存、借出、预约、调拨出入量与平均耗时、丢损 */
    public record LibraryStat(
            Long libraryId,
            String code,
            String name,
            boolean remote,
            boolean tempClosed,
            long copiesHere,
            long onLoan,
            long activeHoldsForPickup,
            long transfersOut,
            long transfersIn,
            Double avgTransitDays,
            long onTimeCount,
            long arrivedCount,
            long lostTransfers
    ) {}

    public record HotBookStat(
            Long bookId,
            String title,
            String author,
            long activeHolds,
            long fulfilledHolds,
            long copies,
            long availableCopies,
            boolean hot
    ) {}

    public record PatronStat(
            Long patronId,
            String cardNo,
            String name,
            long activeLoans,
            long overdueLoans,
            BigDecimal outstandingFines,
            String status
    ) {}

    /** 物流批次视角：一个包裹一行 */
    public record TransferStat(
            Long transferId,
            String packageNo,
            String fromLibrary,
            String toLibrary,
            String status,
            String etaDate,
            String pickedAt,
            String arrivedAt,
            Long transitDays,
            Boolean onTime,
            String bookTitle,
            String barcode
    ) {}

    public record AdminBundle(
            Overview overview,
            List<LibraryStat> byLibrary,
            List<HotBookStat> hotBooks,
            List<PatronStat> patrons,
            List<TransferStat> transfers
    ) {}
}
