package com.citylibrary.pll.service;

import com.citylibrary.pll.domain.AuditLog;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审计留痕：馆员的每一次人工调整（罚款减免、异常判定、闭馆）都记录规则依据与影响。 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(String entityType, Long entityId, String action, String detail,
                    String ruleRef, String operator) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setDetail(detail);
        log.setRuleRef(ruleRef);
        log.setOperator(operator == null ? "SYSTEM" : operator);
        auditLogRepository.save(log);
    }
}
