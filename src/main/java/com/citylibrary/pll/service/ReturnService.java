package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.exception.NotFoundException;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 还书核验：工作人员扫条码后核验
 * 1) 条码是否存在、是否在借（防止重复入库）；
 * 2) 馆藏状态；
 * 3) 污损、缺页、附件；
 * 4) 逾期罚款结算；
 * 5) 跨馆还书：副本落在归还馆并立即参与该馆排队补给。
 */
@Service
public class ReturnService {

    private final BookCopyRepository copyRepository;
    private final LibraryRepository libraryRepository;
    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final HoldRepository holdRepository;
    private final FineService fineService;
    private final HoldService holdService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final TimeService time;

    public ReturnService(BookCopyRepository copyRepository, LibraryRepository libraryRepository,
                         LoanRepository loanRepository, BookRepository bookRepository,
                         HoldRepository holdRepository, FineService fineService,
                         HoldService holdService,
                         AuditService auditService, RuleBookService ruleBook, TimeService time) {
        this.copyRepository = copyRepository;
        this.libraryRepository = libraryRepository;
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.holdRepository = holdRepository;
        this.fineService = fineService;
        this.holdService = holdService;
        this.auditService = auditService;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    public record ReturnResult(Long loanId, String barcode, boolean overdue,
                               List<Long> fineIds, String copyStatus, String note) {}

    @Transactional
    public ReturnResult returnBook(String barcode, String returnLibraryCode,
                                   CopyCondition condition, boolean missingPages,
                                   boolean attachmentsMissing, String damageNote, String operator) {
        BookCopy copy = copyRepository.findLockedByBarcode(barcode)
                .orElseThrow(() -> new NotFoundException("馆藏条码不存在: " + barcode));
        Library returnLibrary = libraryRepository.findByCode(returnLibraryCode)
                .orElseThrow(() -> new NotFoundException("归还馆不存在: " + returnLibraryCode));

        Loan loan = loanRepository.findLockedActiveByCopy(copy.getId(), LoanStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException(
                        "条码 " + barcode + " 没有在借记录（可能已归还入库，请勿重复操作）"));
        if (copy.getStatus() != CopyStatus.ON_LOAN) {
            throw new IllegalStateException("馆藏状态为 " + copy.getStatus() + "，与在借记录不一致，需人工核查");
        }

        List<Long> fineIds = new ArrayList<>();
        boolean overdue = time.today().isAfter(loan.getDueDate());

        // 1. 逾期罚款（滚动更新同一张单）
        Fine overdueFine = fineService.refreshOverdueFine(loan, time.today());
        if (overdueFine != null && overdueFine.getAmount().signum() > 0) {
            fineIds.add(overdueFine.getId());
        }

        // 2. 污损 / 缺页 / 附件核验
        boolean severe = missingPages || attachmentsMissing
                || condition == CopyCondition.SEVERE;
        boolean damaged = severe || condition == CopyCondition.DAMAGED;
        if (damaged) {
            String detail = damageNote == null ? "" : damageNote;
            if (missingPages) {
                detail += " 存在缺页；";
            }
            if (attachmentsMissing) {
                detail += " 附件缺失（光盘/手册等）；";
            }
            Fine damageFine = fineService.chargeDamage(loan, severe, detail.strip());
            fineIds.add(damageFine.getId());
        }

        // 3. 关闭借阅
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnedAt(time.now());
        loan.setReturnLibrary(returnLibrary);

        // 4. 副本去向
        copy.setCurrentLibrary(returnLibrary);
        String note;
        if (damaged) {
            copy.setStatus(CopyStatus.DAMAGED);
            copy.setConditionState(severe ? CopyCondition.SEVERE : CopyCondition.DAMAGED);
            note = "图书污损下架，等待修复/注销决策";
            auditService.log("COPY", copy.getId(), "DAMAGED_ON_RETURN",
                    (severe ? "严重污损" : "一般污损") + "，已下架并开赔偿单",
                    RuleBookService.R_DAMAGE_FINE, operator);
        } else {
            copy.setStatus(CopyStatus.AVAILABLE);
            if (condition == CopyCondition.WORN) {
                copy.setConditionState(CopyCondition.WORN);
            }
            note = overdue ? "逾期归还，已计罚款" : "正常归还";
        }

        auditService.log("LOAN", loan.getId(), "RETURN",
                "在 " + returnLibrary.getCode() + " 归还，条码 " + barcode
                        + "，核验" + (damaged ? "发现污损" : "完好"),
                overdue ? RuleBookService.R_OVERDUE_FINE : null, operator);

        // 5. 完好副本立即参与排队补给（跨馆还书可直接服务排队，必要时再生成调拨）
        if (!damaged) {
            Book book = bookRepository.findLockedById(copy.getBook().getId()).orElseThrow();
            holdService.fillQueue(book);
        }

        return new ReturnResult(loan.getId(), barcode, overdue, fineIds,
                copy.getStatus().name(), note);
    }
}
