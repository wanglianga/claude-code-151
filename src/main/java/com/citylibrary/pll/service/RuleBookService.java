package com.citylibrary.pll.service;

import com.citylibrary.pll.config.RulesProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则手册：所有规则编号集中定义，罚款单 / 审计日志记录 ruleRef，
 * 馆员处理异常时可以在管理端查看规则原文与人工调整的影响。
 */
@Service
public class RuleBookService {

    // ---- 预约与排队 ----
    public static final String R_HOLD_DUPLICATE = "R-HOLD-01";
    public static final String R_HOLD_PICKUP_TIMEOUT = "R-HOLD-02";
    public static final String R_HOLD_CANCEL = "R-HOLD-03";
    public static final String R_HOTBOOK = "R-HOLD-04";

    // ---- 调拨与库存保护 ----
    public static final String R_RETAIN_RATIO = "R-TRANSFER-01";
    public static final String R_TRANSFER_SLA = "R-TRANSFER-02";
    public static final String R_REMOTE_PROTECTION = "R-TRANSFER-03";
    public static final String R_CLOSURE = "R-TRANSFER-04";

    // ---- 借阅与续借 ----
    public static final String R_LOAN_PERIOD = "R-LOAN-01";
    public static final String R_RENEW_LIMIT = "R-LOAN-02";
    public static final String R_RENEW_QUEUE_CONFLICT = "R-LOAN-03";

    // ---- 罚款与赔偿 ----
    public static final String R_OVERDUE_FINE = "R-FINE-01";
    public static final String R_OVERDUE_CAP = "R-FINE-02";
    public static final String R_DAMAGE_FINE = "R-FINE-03";
    public static final String R_LOST_COMPENSATION = "R-FINE-04";
    public static final String R_TRANSFER_LOST_NO_CHARGE = "R-FINE-05";
    public static final String R_MANUAL_ADJUST = "R-FINE-06";
    public static final String R_FINE_BLOCK = "R-FINE-07";

    private final RulesProperties props;

    public RuleBookService(RulesProperties props) {
        this.props = props;
    }

    public Map<String, String> allRules() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(R_HOLD_DUPLICATE, "同一读者对同一书目只能有一条有效预约，避免占用多个排队名额。");
        m.put(R_HOLD_PICKUP_TIMEOUT, "图书到馆后保留 " + props.holdPickupDays()
                + " 天，超时未取自动取消并释放给下一位预约者。");
        m.put(R_HOLD_CANCEL, "读者可在取书前取消预约；已锁条码随之释放并继续服务排队队列。");
        m.put(R_HOTBOOK, "排队人数达到 " + props.hotHoldThreshold()
                + " 人视为热门书：热门书不可续借，保障公平流转。");
        m.put(R_RETAIN_RATIO, "普通馆调拨后须保留至少 " + pct(props.localRetainRatio())
                + " 的本地副本，避免单一馆库存被抽空。");
        m.put(R_TRANSFER_SLA, "普通馆间调拨预计 " + props.normalSlaDays()
                + " 天到馆，远端馆 " + props.remoteSlaDays() + " 天。");
        m.put(R_REMOTE_PROTECTION, "远端分馆留存比例为 " + pct(props.remoteRetainRatio())
                + "，且普通馆有可调副本时不从远端馆调拨，保护偏远读者。");
        m.put(R_CLOSURE, "临时闭馆期间不向该馆出库/到馆；已到馆预约顺延取书期限，在途包裹改派或回退。");
        m.put(R_LOAN_PERIOD, "常规借阅周期 " + props.loanPeriodDays() + " 天，从借出当日起算。");
        m.put(R_RENEW_LIMIT, "每本书最多续借 " + props.renewLimit()
                + " 次，防止热门书被少数读者长期占用。");
        m.put(R_RENEW_QUEUE_CONFLICT, "书后存在有效排队或为热门书时不可续借，到期需归还流转给下一位读者。");
        m.put(R_OVERDUE_FINE, "逾期每日罚款 " + props.overdueDailyFine() + " 元，按实际逾期天数计算。");
        m.put(R_OVERDUE_CAP, "单笔借阅逾期罚款上限 " + props.overdueCapPerLoan() + " 元。");
        m.put(R_DAMAGE_FINE, "归还核验发现污损赔偿 " + props.damageFine()
                + " 元；缺页/附件遗失等严重污损赔偿 " + props.severeDamageFine() + " 元，图书下架。");
        m.put(R_LOST_COMPENSATION, "确认读者遗失按书目定价赔偿，赔款结清后读者状态恢复。");
        m.put(R_TRANSFER_LOST_NO_CHARGE, "物流丢包由馆方承担：不向读者收取赔偿，排队自动重分配，副本标记遗失。");
        m.put(R_MANUAL_ADJUST, "馆员可人工减免/调整罚款，必须填写原因并留痕，审计日志可回看调整影响。");
        m.put(R_FINE_BLOCK, "待缴罚款累计达到 " + props.blockOutstandingAmount()
                + " 元时暂停新借阅与预约，结清后自动恢复。");
        return m;
    }

    public RulesProperties parameters() {
        return props;
    }

    private String pct(double ratio) {
        return Math.round(ratio * 100) + "%";
    }
}
