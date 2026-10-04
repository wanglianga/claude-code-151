package com.citylibrary.pll.service;

import com.citylibrary.pll.config.RulesProperties;
import com.citylibrary.pll.domain.FineStatus;
import com.citylibrary.pll.domain.Patron;
import com.citylibrary.pll.exception.BusinessRuleException;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.FineRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 读者借阅资格：待缴罚款达到阈值时暂停借阅与预约（R-FINE-07）。 */
@Service
public class PatronBlockService {

    private final FineRepository fineRepository;
    private final RulesProperties rules;

    public PatronBlockService(FineRepository fineRepository, RulesProperties rules) {
        this.fineRepository = fineRepository;
        this.rules = rules;
    }

    public BigDecimal outstanding(Long patronId) {
        return fineRepository.sumOutstanding(patronId, FineStatus.OUTSTANDING);
    }

    public void checkBorrowAllowed(Patron patron) {
        BigDecimal debt = outstanding(patron.getId());
        if (debt.compareTo(rules.blockOutstandingAmount()) >= 0) {
            throw new BusinessRuleException(RuleBookService.R_FINE_BLOCK,
                    "待缴罚款 " + debt + " 元已达停借阈值 " + rules.blockOutstandingAmount()
                            + " 元，请先结清罚款");
        }
    }
}
