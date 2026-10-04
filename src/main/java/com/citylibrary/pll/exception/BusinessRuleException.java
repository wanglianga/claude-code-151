package com.citylibrary.pll.exception;

/** 业务规则不满足（续借冲突、留存比例、闭馆等），返回 400。 */
public class BusinessRuleException extends RuntimeException {
    private final String ruleRef;

    public BusinessRuleException(String ruleRef, String message) {
        super(message);
        this.ruleRef = ruleRef;
    }

    public String getRuleRef() {
        return ruleRef;
    }
}
