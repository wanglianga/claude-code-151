package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.exception.NotFoundException;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 异常事件处理：
 * - RETURN_CLAIM 读者声称已还但系统未入库：开争议单；
 *     · 馆内找到：按"读者申报日"核销归还，争议处理期间不累计逾期罚款；
 *     · 确认遗失：读者按定价赔偿，副本与借阅置 LOST。
 * - 读者主动申报遗失：直接赔偿流程。
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final LoanRepository loanRepository;
    private final BookCopyRepository copyRepository;
    private final LibraryRepository libraryRepository;
    private final FineRepository fineRepository;
    private final FineService fineService;
    private final HoldService holdService;
    private final BookRepository bookRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final TimeService time;

    public IncidentService(IncidentRepository incidentRepository, LoanRepository loanRepository,
                           BookCopyRepository copyRepository, LibraryRepository libraryRepository,
                           FineRepository fineRepository, FineService fineService,
                           HoldService holdService, BookRepository bookRepository,
                           NotificationService notificationService, AuditService auditService,
                           RuleBookService ruleBook, TimeService time) {
        this.incidentRepository = incidentRepository;
        this.loanRepository = loanRepository;
        this.copyRepository = copyRepository;
        this.libraryRepository = libraryRepository;
        this.fineRepository = fineRepository;
        this.fineService = fineService;
        this.holdService = holdService;
        this.bookRepository = bookRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    /** 读者声称已还：登记争议单（每笔在借借阅同一时刻只允许一张未结单）。 */
    @Transactional
    public Incident fileReturnClaim(Long loanId, String description, String operator) {
        Loan loan = loanRepository.findLockedById(loanId)
                .orElseThrow(() -> new NotFoundException("借阅记录不存在: " + loanId));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("借阅已结束，无需申报");
        }
        if (incidentRepository.existsByLoanIdAndTypeAndStatus(loanId, IncidentType.RETURN_CLAIM, "OPEN")) {
            throw new BusinessRuleException(null, "该借阅已有处理中的归还争议单");
        }
        Incident inc = new Incident();
        inc.setType(IncidentType.RETURN_CLAIM);
        inc.setLoan(loan);
        inc.setCopy(loan.getCopy());
        inc.setDescription("读者声称图书已归还但系统未入库：" + description
                + "（申报日 " + time.today() + "）");
        inc.setCreatedAt(time.now());
        incidentRepository.save(inc);
        auditService.log("INCIDENT", inc.getId(), "OPEN_RETURN_CLAIM",
                inc.getDescription(), null, operator == null ? "PATRON" : operator);
        return inc;
    }

    /** 馆内找到：按申报日核销，争议期不滚逾期罚款；书回架并补给队列。 */
    @Transactional
    public Incident resolveClaimFound(Long incidentId, String foundLibraryCode, String operator) {
        Incident inc = getOpen(incidentId);
        Loan loan = loanRepository.findLockedById(inc.getLoan().getId()).orElseThrow();
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("借阅状态已变化: " + loan.getStatus());
        }
        Library foundLibrary = libraryRepository.findByCode(foundLibraryCode)
                .orElseThrow(() -> new NotFoundException("馆代码不存在: " + foundLibraryCode));
        BookCopy copy = copyRepository.findLockedById(loan.getCopy().getId()).orElseThrow();

        // 逾期罚款只计算到读者申报日，处理耗时由馆方承担
        Fine fine = fineService.refreshOverdueFine(loan, inc.getCreatedAt().toLocalDate());

        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnedAt(inc.getCreatedAt());
        loan.setReturnLibrary(foundLibrary);
        copy.setCurrentLibrary(foundLibrary);
        copy.setStatus(CopyStatus.AVAILABLE);

        inc.setStatus("RESOLVED");
        inc.setResolution("在 " + foundLibrary.getName() + " 找到并补录入库；按申报日 "
                + inc.getCreatedAt().toLocalDate() + " 核销，"
                + (fine != null && fine.getAmount().signum() > 0
                        ? "计逾期罚款 " + fine.getAmount() + " 元" : "无逾期罚款"));
        inc.setOperator(operator);
        inc.setResolvedAt(time.now());
        auditService.log("INCIDENT", inc.getId(), "RESOLVE_FOUND", inc.getResolution(),
                RuleBookService.R_OVERDUE_FINE, operator);
        notificationService.notify(loan.getPatron(), NoticeType.RETURN_CLAIM_RESULT,
                "归还争议处理结果", "您申报已还的图书已在馆内找到，已为您办结归还，感谢配合。");

        holdService.fillQueue(copy.getBook());
        return inc;
    }

    /** 确认找不到：读者遗失，按定价赔偿。 */
    @Transactional
    public Incident resolveClaimLost(Long incidentId, String operator) {
        Incident inc = getOpen(incidentId);
        Loan loan = loanRepository.findLockedById(inc.getLoan().getId()).orElseThrow();
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("借阅状态已变化: " + loan.getStatus());
        }
        chargeLostAndClose(loan, "归还争议经核查确认图书遗失");
        inc.setStatus("RESOLVED");
        inc.setResolution("未找到图书，按定价 " + loan.getCopy().getBook().getPrice()
                + " 元向读者开具遗失赔偿单");
        inc.setOperator(operator);
        inc.setResolvedAt(time.now());
        auditService.log("INCIDENT", inc.getId(), "RESOLVE_LOST", inc.getResolution(),
                RuleBookService.R_LOST_COMPENSATION, operator);
        notificationService.notify(loan.getPatron(), NoticeType.RETURN_CLAIM_RESULT,
                "归还争议处理结果", "经核查暂未找到该书，已开具遗失赔偿单；如 30 日内找回可申请退款。");
        return inc;
    }

    /** 读者/馆员直接申报遗失。 */
    @Transactional
    public Fine reportReaderLost(Long loanId, String reason, String operator) {
        Loan loan = loanRepository.findLockedById(loanId)
                .orElseThrow(() -> new NotFoundException("借阅记录不存在: " + loanId));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new IllegalStateException("仅在借图书可申报遗失");
        }
        Fine fine = chargeLostAndClose(loan, reason);
        auditService.log("INCIDENT", null, "READER_LOST",
                "读者申报遗失，借阅 " + loanId, RuleBookService.R_LOST_COMPENSATION,
                operator == null ? "PATRON" : operator);
        return fine;
    }

    private Fine chargeLostAndClose(Loan loan, String reason) {
        BookCopy copy = loan.getCopy();
        Fine fine = fineService.chargeLostCompensation(loan, copy.getBook().getPrice(), reason);
        loan.setStatus(LoanStatus.LOST);
        copy.setStatus(CopyStatus.LOST);
        return fine;
    }

    /** 争议单关闭后遗失图书又找回：退款/撤销赔偿，副本回架。 */
    @Transactional
    public Incident reopenCompensationAfterFound(Long incidentId, String foundLibraryCode,
                                                 String operator) {
        Incident inc = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("异常单不存在: " + incidentId));
        Library lib = libraryRepository.findByCode(foundLibraryCode)
                .orElseThrow(() -> new NotFoundException("馆代码不存在: " + foundLibraryCode));
        Loan loan = loanRepository.findLockedById(inc.getLoan().getId()).orElseThrow();
        Fine comp = fineRepository.findByLoanIdAndType(loan.getId(), FineType.LOST_COMPENSATION)
                .orElseThrow(() -> new IllegalStateException("该单无遗失赔偿记录"));
        if (comp.getStatus() == FineStatus.PAID) {
            throw new BusinessRuleException(RuleBookService.R_MANUAL_ADJUST,
                    "赔偿已缴纳，请走线下退款流程后由馆长审批冲正");
        }
        comp.setStatus(FineStatus.WAIVED);
        comp.setAmount(java.math.BigDecimal.ZERO);
        comp.setManual(true);
        comp.setOperator(operator);
        comp.setSettledAt(time.now());
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnedAt(time.now());
        loan.setReturnLibrary(lib);
        BookCopy copy = copyRepository.findLockedById(loan.getCopy().getId()).orElseThrow();
        copy.setCurrentLibrary(lib);
        copy.setStatus(CopyStatus.AVAILABLE);
        inc.setResolution((inc.getResolution() == null ? "" : inc.getResolution())
                + "；图书找回，赔偿撤销，已回架 " + lib.getCode());
        auditService.log("INCIDENT", inc.getId(), "FOUND_AFTER_LOST",
                "图书找回，撤销遗失赔偿并回架", RuleBookService.R_MANUAL_ADJUST, operator);
        holdService.fillQueue(copy.getBook());
        return inc;
    }

    @Transactional(readOnly = true)
    public List<Incident> openIncidents() {
        return incidentRepository.findByStatusOrderByCreatedAtDesc("OPEN");
    }

    @Transactional(readOnly = true)
    public List<Incident> all() {
        return incidentRepository.findAll();
    }

    private Incident getOpen(Long id) {
        Incident inc = incidentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("异常单不存在: " + id));
        if (!"OPEN".equals(inc.getStatus())) {
            throw new IllegalStateException("异常单已办结");
        }
        return inc;
    }
}
