package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.dto.StatsDtos;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端统计：按馆 / 书目 / 读者 / 物流批次四个视角
 * 查看调拨效率、热门预约、逾期与丢损。聚合在应用层完成，便于规则口径统一维护。
 */
@Service
public class AdminService {

    private static final List<HoldStatus> ACTIVE_HOLDS = List.of(HoldStatus.QUEUED, HoldStatus.READY);

    private final LibraryRepository libraryRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final HoldRepository holdRepository;
    private final LoanRepository loanRepository;
    private final TransferRepository transferRepository;
    private final FineRepository fineRepository;
    private final PatronRepository patronRepository;
    private final AuditLogRepository auditLogRepository;
    private final RuleBookService ruleBook;
    private final TimeService time;

    public AdminService(LibraryRepository libraryRepository, BookRepository bookRepository,
                        BookCopyRepository copyRepository, HoldRepository holdRepository,
                        LoanRepository loanRepository, TransferRepository transferRepository,
                        FineRepository fineRepository, PatronRepository patronRepository,
                        AuditLogRepository auditLogRepository, RuleBookService ruleBook,
                        TimeService time) {
        this.libraryRepository = libraryRepository;
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.holdRepository = holdRepository;
        this.loanRepository = loanRepository;
        this.transferRepository = transferRepository;
        this.fineRepository = fineRepository;
        this.patronRepository = patronRepository;
        this.auditLogRepository = auditLogRepository;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public StatsDtos.AdminBundle bundle() {
        List<Library> libraries = libraryRepository.findAll();
        List<Book> books = bookRepository.findAll();
        List<BookCopy> copies = copyRepository.findAll();
        List<Hold> holds = holdRepository.findAll();
        List<Loan> loans = loanRepository.findAll();
        List<Transfer> transfers = transferRepository.findAll();
        List<Patron> patrons = patronRepository.findAll();

        return new StatsDtos.AdminBundle(
                overview(copies, holds, loans, transfers, libraries, books),
                byLibrary(libraries, copies, holds, transfers),
                hotBooks(books, copies, holds),
                patronStats(patrons, loans),
                transferStats(transfers));
    }

    private StatsDtos.Overview overview(List<BookCopy> copies, List<Hold> holds, List<Loan> loans,
                                        List<Transfer> transfers, List<Library> libraries,
                                        List<Book> books) {
        BigDecimal outstanding = finesOutstanding();
        long overdue = loans.stream().filter(l -> l.getStatus() == LoanStatus.ACTIVE
                && l.getDueDate().isBefore(time.today())).count();
        return new StatsDtos.Overview(
                libraries.size(),
                books.size(),
                copies.size(),
                copies.stream().filter(c -> c.getStatus() == CopyStatus.AVAILABLE).count(),
                holds.stream().filter(h -> ACTIVE_HOLDS.contains(h.getStatus())).count(),
                holds.stream().filter(h -> h.getStatus() == HoldStatus.READY).count(),
                loans.stream().filter(l -> l.getStatus() == LoanStatus.ACTIVE).count(),
                overdue,
                transfers.stream().filter(t -> t.getStatus() == TransferStatus.CREATED
                        || t.getStatus() == TransferStatus.PICKED_UP).count(),
                transfers.stream().filter(t -> t.getStatus() == TransferStatus.LOST).count(),
                copies.stream().filter(c -> c.getStatus() == CopyStatus.DAMAGED).count(),
                copies.stream().filter(c -> c.getStatus() == CopyStatus.LOST).count(),
                outstanding);
    }

    private List<StatsDtos.LibraryStat> byLibrary(List<Library> libraries, List<BookCopy> copies,
                                                  List<Hold> holds, List<Transfer> transfers) {
        return libraries.stream().map(lib -> {
            long copiesHere = copies.stream()
                    .filter(c -> c.getCurrentLibrary().getId().equals(lib.getId())).count();
            long onLoan = copies.stream()
                    .filter(c -> c.getCurrentLibrary().getId().equals(lib.getId())
                            && c.getStatus() == CopyStatus.ON_LOAN).count();
            long pickupHolds = holds.stream()
                    .filter(h -> h.getPickupLibrary().getId().equals(lib.getId())
                            && ACTIVE_HOLDS.contains(h.getStatus())).count();
            List<Transfer> out = transfers.stream()
                    .filter(t -> t.getFromLibrary().getId().equals(lib.getId())).toList();
            List<Transfer> in = transfers.stream()
                    .filter(t -> t.getToLibrary().getId().equals(lib.getId())).toList();

            List<Transfer> arrived = transfers.stream()
                    .filter(t -> t.getStatus() == TransferStatus.ARRIVED
                            && t.getToLibrary().getId().equals(lib.getId())
                            && t.getPickedAt() != null && t.getArrivedAt() != null)
                    .toList();
            Double avg = arrived.isEmpty() ? null : arrived.stream()
                    .mapToLong(t -> ChronoUnit.DAYS.between(t.getPickedAt(), t.getArrivedAt()))
                    .average().orElse(0);
            long onTime = arrived.stream()
                    .filter(t -> t.getEtaDate() == null
                            || !t.getArrivedAt().toLocalDate().isAfter(t.getEtaDate()))
                    .count();
            long lost = transfers.stream()
                    .filter(t -> t.getStatus() == TransferStatus.LOST
                            && (t.getFromLibrary().getId().equals(lib.getId())
                                || t.getToLibrary().getId().equals(lib.getId())))
                    .count();
            return new StatsDtos.LibraryStat(lib.getId(), lib.getCode(), lib.getName(),
                    lib.isRemote(), lib.isTempClosed(), copiesHere, onLoan, pickupHolds,
                    out.size(), in.size(), avg, onTime, arrived.size(), lost);
        }).toList();
    }

    private List<StatsDtos.HotBookStat> hotBooks(List<Book> books, List<BookCopy> copies,
                                                 List<Hold> holds) {
        int threshold = ruleBook.parameters().hotHoldThreshold();
        return books.stream().map(book -> {
            long active = holds.stream()
                    .filter(h -> h.getBook().getId().equals(book.getId())
                            && ACTIVE_HOLDS.contains(h.getStatus())).count();
            long fulfilled = holds.stream()
                    .filter(h -> h.getBook().getId().equals(book.getId())
                            && h.getStatus() == HoldStatus.FULFILLED).count();
            long bookCopies = copies.stream()
                    .filter(c -> c.getBook().getId().equals(book.getId())
                            && c.getStatus() != CopyStatus.WITHDRAWN).count();
            long available = copies.stream()
                    .filter(c -> c.getBook().getId().equals(book.getId())
                            && c.getStatus() == CopyStatus.AVAILABLE).count();
            return new StatsDtos.HotBookStat(book.getId(), book.getTitle(), book.getAuthor(),
                    active, fulfilled, bookCopies, available, active >= threshold);
        }).sorted(Comparator.comparingLong(StatsDtos.HotBookStat::activeHolds).reversed())
                .limit(50).toList();
    }

    private List<StatsDtos.PatronStat> patronStats(List<Patron> patrons, List<Loan> loans) {
        return patrons.stream().map(p -> {
            List<Loan> active = loans.stream()
                    .filter(l -> l.getPatron().getId().equals(p.getId())
                            && l.getStatus() == LoanStatus.ACTIVE).toList();
            long overdue = active.stream().filter(l -> l.getDueDate().isBefore(time.today())).count();
            return new StatsDtos.PatronStat(p.getId(), p.getCardNo(), p.getName(),
                    active.size(), overdue, finesOutstandingFor(p.getId()), p.getStatus().name());
        }).sorted(Comparator.comparing(StatsDtos.PatronStat::outstandingFines).reversed())
                .toList();
    }

    private List<StatsDtos.TransferStat> transferStats(List<Transfer> transfers) {
        return transfers.stream()
                .sorted(Comparator.comparing(Transfer::getId).reversed())
                .map(t -> {
                    Long transitDays = (t.getPickedAt() != null && t.getArrivedAt() != null)
                            ? ChronoUnit.DAYS.between(t.getPickedAt(), t.getArrivedAt()) : null;
                    Boolean onTime = t.getStatus() == TransferStatus.ARRIVED && t.getEtaDate() != null
                            ? !t.getArrivedAt().toLocalDate().isAfter(t.getEtaDate()) : null;
                    return new StatsDtos.TransferStat(
                            t.getId(),
                            t.getPackageNo(),
                            t.getFromLibrary().getCode() + " " + t.getFromLibrary().getName(),
                            t.getToLibrary().getCode() + " " + t.getToLibrary().getName(),
                            t.getStatus().name(),
                            t.getEtaDate() == null ? null : t.getEtaDate().toString(),
                            t.getPickedAt() == null ? null : t.getPickedAt().toLocalDate().toString(),
                            t.getArrivedAt() == null ? null : t.getArrivedAt().toLocalDate().toString(),
                            transitDays,
                            onTime,
                            t.getCopy().getBook().getTitle(),
                            t.getCopy().getBarcode());
                }).toList();
    }

    private BigDecimal finesOutstanding() {
        return fineRepository.findAll().stream()
                .filter(f -> f.getStatus() == FineStatus.OUTSTANDING)
                .map(Fine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal finesOutstandingFor(Long patronId) {
        return fineRepository.findByPatronIdOrderByCreatedAtDesc(patronId).stream()
                .filter(f -> f.getStatus() == FineStatus.OUTSTANDING)
                .map(Fine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recentAudit() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> auditFor(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }
}
