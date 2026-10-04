package com.citylibrary.pll.dto;

import com.citylibrary.pll.domain.*;

import java.time.OffsetDateTime;

/** 实体到 API 视图的统一映射，避免实体直接外泄（屏蔽懒加载陷阱）。 */
public final class Views {

    private Views() {}

    public static LibraryView of(Library l) {
        return new LibraryView(l.getId(), l.getCode(), l.getName(), l.getDistrict(),
                l.isRemote(), l.isTempClosed(), l.getReopenDate() == null ? null : l.getReopenDate().toString());
    }

    public static BookView of(Book b) {
        return new BookView(b.getId(), b.getIsbn(), b.getTitle(), b.getAuthor(),
                b.getPublisher(), b.getPublishYear(), b.isEbookAvailable(), b.getEbookUrl(),
                b.getPrice());
    }

    public static CopyView of(BookCopy c) {
        return new CopyView(c.getId(), c.getBarcode(), c.getBook().getId(),
                c.getBook().getTitle(), c.getOwningLibrary().getCode(),
                c.getCurrentLibrary().getCode(), c.getStatus().name(),
                c.getConditionState().name());
    }

    public static PatronView of(Patron p) {
        return new PatronView(p.getId(), p.getCardNo(), p.getName(), p.getEmail(), p.getStatus().name());
    }

    public static HoldView of(Hold h) {
        return new HoldView(h.getId(), h.getBook().getId(), h.getBook().getTitle(),
                h.getPatron().getCardNo(), h.getPatron().getName(),
                h.getPickupLibrary().getCode(), h.getPickupLibrary().getName(),
                h.getReservedCopy() == null ? null : h.getReservedCopy().getBarcode(),
                h.getStatus().name(), h.getQueueSeq(),
                str(h.getReadyAt()), str(h.getExpiresAt()),
                h.getFulfilledLoan() == null ? null : h.getFulfilledLoan().getId(),
                h.getCancelReason(), str(h.getCreatedAt()));
    }

    public static TransferView of(Transfer t) {
        return new TransferView(t.getId(), t.getPackageNo(), t.getCopy().getBarcode(),
                t.getCopy().getBook().getTitle(),
                t.getFromLibrary().getCode(), t.getToLibrary().getCode(),
                t.getHold() == null ? null : t.getHold().getId(),
                t.getStatus().name(),
                t.getEtaDate() == null ? null : t.getEtaDate().toString(),
                str(t.getPickedAt()), str(t.getArrivedAt()), t.getNote());
    }

    public static LoanView of(Loan l) {
        return new LoanView(l.getId(), l.getPatron().getCardNo(), l.getPatron().getName(),
                l.getCopy().getBarcode(), l.getCopy().getBook().getTitle(),
                l.getCheckoutLibrary().getCode(),
                l.getDueDate().toString(), l.getRenewedCount(), l.getStatus().name(),
                str(l.getReturnedAt()),
                l.getReturnLibrary() == null ? null : l.getReturnLibrary().getCode());
    }

    public static FineView of(Fine f) {
        return new FineView(f.getId(), f.getPatron().getCardNo(),
                f.getLoan() == null ? null : f.getLoan().getId(),
                f.getType().name(), f.getAmount(), f.getStatus().name(), f.getReason(),
                f.getRuleRef(), f.isManual(), f.getOperator(), str(f.getCreatedAt()));
    }

    public static IncidentView of(Incident i) {
        return new IncidentView(i.getId(), i.getType().name(),
                i.getCopy() == null ? null : i.getCopy().getBarcode(),
                i.getLoan() == null ? null : i.getLoan().getId(),
                i.getHold() == null ? null : i.getHold().getId(),
                i.getDescription(), i.getResolution(), i.getStatus(), i.getOperator(),
                str(i.getCreatedAt()), str(i.getResolvedAt()));
    }

    public static NoticeView of(Notice n) {
        return new NoticeView(n.getId(), n.getType().name(), n.getTitle(), n.getContent(),
                n.isReadFlag(), str(n.getCreatedAt()));
    }

    public static AuditView of(AuditLog a) {
        return new AuditView(a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(),
                a.getDetail(), a.getRuleRef(), a.getOperator(), str(a.getCreatedAt()));
    }

    private static String str(OffsetDateTime t) {
        return t == null ? null : t.toString();
    }

    public record LibraryView(Long id, String code, String name, String district,
                              boolean remote, boolean tempClosed, String reopenDate) {}

    public record BookView(Long id, String isbn, String title, String author, String publisher,
                           Integer publishYear, boolean ebookAvailable, String ebookUrl,
                           java.math.BigDecimal price) {}

    public record CopyView(Long id, String barcode, Long bookId, String title,
                           String owningLibrary, String currentLibrary,
                           String status, String condition) {}

    public record PatronView(Long id, String cardNo, String name, String email, String status) {}

    public record HoldView(Long id, Long bookId, String title, String cardNo, String patronName,
                           String pickupLibrary, String pickupLibraryName,
                           String reservedBarcode, String status, long queueSeq,
                           String readyAt, String expiresAt, Long fulfilledLoanId,
                           String cancelReason, String createdAt) {}

    public record TransferView(Long id, String packageNo, String barcode, String title,
                               String fromLibrary, String toLibrary, Long holdId, String status,
                               String etaDate, String pickedAt, String arrivedAt, String note) {}

    public record LoanView(Long id, String cardNo, String patronName, String barcode, String title,
                           String checkoutLibrary, String dueDate, int renewedCount, String status,
                           String returnedAt, String returnLibrary) {}

    public record FineView(Long id, String cardNo, Long loanId, String type,
                           java.math.BigDecimal amount, String status, String reason,
                           String ruleRef, boolean manual, String operator, String createdAt) {}

    public record IncidentView(Long id, String type, String barcode, Long loanId, Long holdId,
                               String description, String resolution, String status,
                               String operator, String createdAt, String resolvedAt) {}

    public record NoticeView(Long id, String type, String title, String content,
                             boolean read, String createdAt) {}

    public record AuditView(Long id, String entityType, Long entityId, String action,
                            String detail, String ruleRef, String operator, String createdAt) {}
}
