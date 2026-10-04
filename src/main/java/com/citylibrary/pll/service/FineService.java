package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.exception.NotFoundException;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.FineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 罚款与赔偿：
 * - 逾期罚款按日累计、单笔封顶（同一借阅仅一张逾期单，金额滚动更新，避免重复罚款）；
 * - 污损/严重污损、遗失赔偿分别开单；
 * - 馆员可减免/调整，必须填写原因，全程审计留痕。
 */
@Service
public class FineService {

    private final FineRepository fineRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final TimeService time;

    public FineService(FineRepository fineRepository, NotificationService notificationService,
                       AuditService auditService, RuleBookService ruleBook, TimeService time) {
        this.fineRepository = fineRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    /** 计算/刷新某笔借阅截至指定日期的逾期罚款，返回罚款单（无逾期且无历史单时返回 null）。 */
    @Transactional
    public Fine refreshOverdueFine(Loan loan, LocalDate asOf) {
        if (loan.getStatus() == LoanStatus.LOST) {
            return null;
        }
        LocalDate effectiveDue = loan.getDueDate();
        long days = ChronoUnit.DAYS.between(effectiveDue, asOf);
        if (days <= 0) {
            return fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE).orElse(null);
        }
        BigDecimal amount = ruleBook.parameters().overdueDailyFine()
                .multiply(BigDecimal.valueOf(days))
                .min(ruleBook.parameters().overdueCapPerLoan());

        Fine fine = fineRepository.findByLoanIdAndType(loan.getId(), FineType.OVERDUE)
                .orElseGet(() -> {
                    Fine f = new Fine();
                    f.setPatron(loan.getPatron());
                    f.setLoan(loan);
                    f.setType(FineType.OVERDUE);
                    f.setStatus(FineStatus.OUTSTANDING);
                    return f;
                });
        if (fine.getStatus() == FineStatus.OUTSTANDING) {
            fine.setAmount(amount);
            fine.setReason("逾期 " + days + " 天（应还日 " + effectiveDue + "），按日计费并受单笔上限约束");
            fine.setRuleRef(RuleBookService.R_OVERDUE_FINE);
            fineRepository.save(fine);
        }
        return fine;
    }

    @Transactional
    public Fine chargeDamage(Loan loan, boolean severe, String detail) {
        Fine f = new Fine();
        f.setPatron(loan.getPatron());
        f.setLoan(loan);
        f.setType(severe ? FineType.SEVERE_DAMAGE : FineType.DAMAGE);
        f.setAmount(severe ? ruleBook.parameters().severeDamageFine()
                : ruleBook.parameters().damageFine());
        f.setReason((severe ? "严重污损（缺页/附件遗失）：" : "一般污损：") + detail);
        f.setRuleRef(RuleBookService.R_DAMAGE_FINE);
        f.setStatus(FineStatus.OUTSTANDING);
        fineRepository.save(f);
        notificationService.notify(loan.getPatron(), loan, NoticeType.FINE_NOTICE,
                "污损赔偿通知", "还书核验发现" + f.getReason() + "，赔偿金额 " + f.getAmount() + " 元。");
        return f;
    }

    @Transactional
    public Fine chargeLostCompensation(Loan loan, BigDecimal price, String reason) {
        Fine f = new Fine();
        f.setPatron(loan.getPatron());
        f.setLoan(loan);
        f.setType(FineType.LOST_COMPENSATION);
        f.setAmount(price);
        f.setReason(reason);
        f.setRuleRef(RuleBookService.R_LOST_COMPENSATION);
        f.setStatus(FineStatus.OUTSTANDING);
        fineRepository.save(f);
        notificationService.notify(loan.getPatron(), loan, NoticeType.FINE_NOTICE,
                "遗失赔偿通知", reason + "，按书目定价赔偿 " + price + " 元。");
        return f;
    }

    @Transactional
    public Fine pay(Long fineId, String operator) {
        Fine f = get(fineId);
        if (f.getStatus() != FineStatus.OUTSTANDING) {
            throw new IllegalStateException("罚款单状态为 " + f.getStatus() + "，无需缴纳");
        }
        f.setStatus(FineStatus.PAID);
        f.setSettledAt(time.now());
        auditService.log("FINE", f.getId(), "PAY",
                "缴纳罚款 " + f.getAmount() + " 元（" + f.getType() + "）", f.getRuleRef(), operator);
        return f;
    }

    /** 馆员减免：金额清零并留痕。 */
    @Transactional
    public Fine waive(Long fineId, String reason, String operator) {
        Fine f = get(fineId);
        requireOperator(operator);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("减免必须填写原因");
        }
        f.setStatus(FineStatus.WAIVED);
        f.setAmount(BigDecimal.ZERO);
        f.setManual(true);
        f.setOperator(operator);
        f.setSettledAt(time.now());
        f.setReason(f.getReason() + "；馆员减免原因：" + reason);
        auditService.log("FINE", f.getId(), "WAIVE",
                "馆员减免罚款，原因：" + reason, RuleBookService.R_MANUAL_ADJUST, operator);
        notificationService.notify(f.getPatron(), f.getLoan(), NoticeType.FINE_NOTICE,
                "罚款调整通知", "您的一笔罚款已由馆员减免，原因：" + reason);
        return f;
    }

    /** 馆员调整金额：金额必须小于原金额（不可擅自加罚）。 */
    @Transactional
    public Fine adjust(Long fineId, BigDecimal newAmount, String reason, String operator) {
        Fine f = get(fineId);
        requireOperator(operator);
        if (newAmount == null || newAmount.signum() < 0) {
            throw new IllegalArgumentException("调整后金额非法");
        }
        if (newAmount.compareTo(f.getAmount()) >= 0) {
            throw new BusinessRuleException(RuleBookService.R_MANUAL_ADJUST,
                    "人工调整只能减免，调整后金额须低于当前金额");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("调整必须填写原因");
        }
        BigDecimal old = f.getAmount();
        f.setAmount(newAmount);
        f.setManual(true);
        f.setOperator(operator);
        f.setStatus(newAmount.signum() == 0 ? FineStatus.WAIVED : FineStatus.ADJUSTED);
        if (newAmount.signum() == 0) {
            f.setSettledAt(time.now());
        }
        f.setReason(f.getReason() + "；馆员调整：" + old + "->" + newAmount + "，原因：" + reason);
        auditService.log("FINE", f.getId(), "ADJUST",
                "馆员调整罚款金额 " + old + "->" + newAmount + "，原因：" + reason,
                RuleBookService.R_MANUAL_ADJUST, operator);
        notificationService.notify(f.getPatron(), f.getLoan(), NoticeType.FINE_NOTICE,
                "罚款调整通知", "您的罚款已由馆员由 " + old + " 元调整为 " + newAmount + " 元。");
        return f;
    }

    @Transactional(readOnly = true)
    public Fine get(Long fineId) {
        return fineRepository.findById(fineId)
                .orElseThrow(() -> new NotFoundException("罚款单不存在: " + fineId));
    }

    @Transactional(readOnly = true)
    public List<Fine> patronFines(Long patronId) {
        return fineRepository.findByPatronIdOrderByCreatedAtDesc(patronId);
    }

    private void requireOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("人工调整必须登记馆员工号");
        }
    }
}
