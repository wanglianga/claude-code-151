package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.exception.NotFoundException;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 借阅与续借。
 * 公平性：
 * - 续借次数受限（R-LOAN-02）；
 * - 书后有排队或为热门书时不可续借（R-LOAN-03 / R-HOLD-04）；
 * - 现场借书也要避让同馆排队：某馆对该书存在活跃预约时，现场可借副本优先给预约者。
 */
@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final HoldRepository holdRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final LibraryRepository libraryRepository;
    private final PatronRepository patronRepository;
    private final NotificationService notificationService;
    private final FineService fineService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final PatronBlockService blockService;
    private final TimeService time;

    public LoanService(LoanRepository loanRepository, HoldRepository holdRepository,
                       BookRepository bookRepository, BookCopyRepository copyRepository,
                       LibraryRepository libraryRepository, PatronRepository patronRepository,
                       NotificationService notificationService, FineService fineService,
                       AuditService auditService, RuleBookService ruleBook,
                       PatronBlockService blockService, TimeService time) {
        this.loanRepository = loanRepository;
        this.holdRepository = holdRepository;
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.libraryRepository = libraryRepository;
        this.patronRepository = patronRepository;
        this.notificationService = notificationService;
        this.fineService = fineService;
        this.auditService = auditService;
        this.blockService = blockService;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    /** 预约取书：馆员扫读者证 + 条码，核验与 READY 预约一致。 */
    @Transactional
    public Loan checkoutHold(Long holdId, String barcode, String operator) {
        Hold hold = holdRepository.findLockedById(holdId)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + holdId));
        if (hold.getStatus() != HoldStatus.READY) {
            throw new IllegalStateException("预约当前状态为 " + hold.getStatus() + "，不能取书");
        }
        if (hold.getReservedCopy() == null
                || !hold.getReservedCopy().getBarcode().equals(barcode)) {
            throw new BusinessRuleException(null, "条码与预约锁定的馆藏不一致");
        }
        if (time.now().isAfter(hold.getExpiresAt())) {
            throw new BusinessRuleException(RuleBookService.R_HOLD_PICKUP_TIMEOUT,
                    "已超过取书期限，请重新预约");
        }
        BookCopy copy = copyRepository.findLockedByBarcode(barcode).orElseThrow();
        if (!copy.getCurrentLibrary().getId().equals(hold.getPickupLibrary().getId())) {
            throw new IllegalStateException("图书不在取书馆，可能仍在调拨中");
        }
        if (copy.getStatus() != CopyStatus.HELD) {
            throw new IllegalStateException("馆藏状态为 " + copy.getStatus() + "，不能借出");
        }
        blockService.checkBorrowAllowed(hold.getPatron());

        Loan loan = createLoan(hold.getPatron(), copy, hold.getPickupLibrary());
        hold.setStatus(HoldStatus.FULFILLED);
        hold.setFulfilledLoan(loan);
        auditService.log("HOLD", hold.getId(), "FULFILL",
                "预约取书借出，借阅号 " + loan.getId(), RuleBookService.R_LOAN_PERIOD, operator);
        return loan;
    }

    /** 现场直接借书（无预约）。 */
    @Transactional
    public Loan checkoutDirect(String cardNo, String barcode, String libraryCode, String operator) {
        Patron patron = patronRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new NotFoundException("读者证不存在: " + cardNo));
        BookCopy copy = copyRepository.findLockedByBarcode(barcode)
                .orElseThrow(() -> new NotFoundException("馆藏条码不存在: " + barcode));
        Library library = libraryRepository.findByCode(libraryCode)
                .orElseThrow(() -> new NotFoundException("图书馆不存在: " + libraryCode));

        blockService.checkBorrowAllowed(patron);
        if (copy.getStatus() != CopyStatus.AVAILABLE) {
            throw new IllegalStateException("该条码当前状态为 " + copy.getStatus() + "，不可借出");
        }
        if (!copy.getCurrentLibrary().getId().equals(library.getId())) {
            throw new IllegalStateException("该书不属于当前馆，请走跨馆预约");
        }
        long queueHere = holdRepository.countActiveAtPickup(
                copy.getBook().getId(), library.getId(), HoldService.ACTIVE);
        if (queueHere > 0) {
            throw new BusinessRuleException(RuleBookService.R_RENEW_QUEUE_CONFLICT,
                    "本书在本馆有 " + queueHere + " 位读者排队，现场借阅请让位给预约队列");
        }
        return createLoan(patron, copy, library);
    }

    private Loan createLoan(Patron patron, BookCopy copy, Library library) {
        Loan loan = new Loan();
        loan.setPatron(patron);
        loan.setCopy(copy);
        loan.setCheckoutLibrary(library);
        loan.setDueDate(time.today().plusDays(ruleBook.parameters().loanPeriodDays()));
        loan.setStatus(LoanStatus.ACTIVE);
        loanRepository.saveAndFlush(loan);

        copy.setStatus(CopyStatus.ON_LOAN);
        auditService.log("LOAN", loan.getId(), "CHECKOUT",
                "读者 " + patron.getCardNo() + " 借出 " + copy.getBarcode()
                        + "，应还日 " + loan.getDueDate(),
                RuleBookService.R_LOAN_PERIOD, "STAFF");
        return loan;
    }

    /** 续借：次数、排队冲突、热门书、已逾期多重校验。 */
    @Transactional
    public Loan renew(Long loanId, String operator) {
        Loan loan = loanRepository.findLockedById(loanId)
                .orElseThrow(() -> new NotFoundException("借阅记录不存在: " + loanId));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("仅借阅中的记录可续借，当前: " + loan.getStatus());
        }
        if (time.today().isAfter(loan.getDueDate())) {
            throw new BusinessRuleException(RuleBookService.R_OVERDUE_FINE,
                    "图书已逾期，请先归还并结清罚款，不能续借");
        }
        if (loan.getRenewedCount() >= ruleBook.parameters().renewLimit()) {
            throw new BusinessRuleException(RuleBookService.R_RENEW_LIMIT,
                    "已达续借上限 " + ruleBook.parameters().renewLimit() + " 次");
        }
        long queue = holdRepository.countActiveForBook(loan.getCopy().getBook().getId(),
                HoldService.ACTIVE);
        if (queue > 0) {
            throw new BusinessRuleException(RuleBookService.R_RENEW_QUEUE_CONFLICT,
                    "该书当前有 " + queue + " 位读者排队，为公平流转不可续借");
        }
        if (queue >= ruleBook.parameters().hotHoldThreshold()) {
            throw new BusinessRuleException(RuleBookService.R_HOTBOOK, "热门书不可续借");
        }
        LocalDate oldDue = loan.getDueDate();
        loan.setRenewedCount(loan.getRenewedCount() + 1);
        loan.setDueDate(time.today().plusDays(ruleBook.parameters().loanPeriodDays()));
        auditService.log("LOAN", loan.getId(), "RENEW",
                "续借，应还日 " + oldDue + " -> " + loan.getDueDate()
                        + "，第 " + loan.getRenewedCount() + " 次续借",
                RuleBookService.R_RENEW_LIMIT, operator == null ? "PATRON" : operator);
        notificationService.notify(loan.getPatron(), loan, NoticeType.DUE_REMINDER,
                "续借成功", "新应还日期为 " + loan.getDueDate());
        return loan;
    }

    /** 定时扫描：到期提醒（3 天内）+ 逾期罚款滚动与通知。 */
    @Transactional
    public int scanOverdueAndRemind() {
        int touched = 0;
        for (Loan loan : loanRepository.findByStatus(LoanStatus.ACTIVE)) {
            long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(time.today(), loan.getDueDate());
            if (daysToDue >= 0 && daysToDue <= 3
                    && !notificationService.existsByTypeAndLoan(NoticeType.DUE_REMINDER, loan.getId())) {
                notificationService.notify(loan.getPatron(), loan, NoticeType.DUE_REMINDER,
                        "图书即将到期", "您借阅的图书将于 " + loan.getDueDate() + " 到期，请按时归还或尝试续借。");
                touched++;
            }
            if (daysToDue < 0) {
                Fine fine = fineService.refreshOverdueFine(loan, time.today());
                if (fine != null
                        && !notificationService.existsByTypeAndLoan(NoticeType.OVERDUE, loan.getId())) {
                    notificationService.notify(loan.getPatron(), loan, NoticeType.OVERDUE,
                            "图书已逾期", "您的图书已逾期，当前罚款 " + fine.getAmount()
                                    + " 元，请尽快归还。");
                }
                touched++;
            }
        }
        return touched;
    }

    @Transactional(readOnly = true)
    public Loan get(Long id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("借阅记录不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<Loan> patronLoans(String cardNo) {
        Patron patron = patronRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new NotFoundException("读者证不存在: " + cardNo));
        return loanRepository.findByPatronIdAndStatus(patron.getId(), LoanStatus.ACTIVE);
    }
}
