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
 * 馆际调拨状态机：
 *   createForHold: 锁定条码并生成 CREATED 任务（含预计到达日）
 *   pickup:  物流员取书出库，登记包裹号，CREATED -> PICKED_UP，副本 IN_TRANSIT
 *   arrive:  到馆登记，PICKED_UP -> ARRIVED，副本所在馆变更并交预约服务通知取书
 *   reportLost: 任意阶段丢包 -> LOST，副本 LOST、开异常单、通知读者、预约自动重分配
 *   cancel:  未出库可随预约取消而取消，副本回架
 */
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final BookCopyRepository copyRepository;
    private final LibraryRepository libraryRepository;
    private final HoldRepository holdRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationService notificationService;
    private final HoldService holdService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final TimeService time;

    public TransferService(TransferRepository transferRepository, BookCopyRepository copyRepository,
                           LibraryRepository libraryRepository, HoldRepository holdRepository,
                           IncidentRepository incidentRepository,
                           NotificationService notificationService, HoldService holdService,
                           AuditService auditService, RuleBookService ruleBook, TimeService time) {
        this.transferRepository = transferRepository;
        this.copyRepository = copyRepository;
        this.libraryRepository = libraryRepository;
        this.holdRepository = holdRepository;
        this.incidentRepository = incidentRepository;
        this.notificationService = notificationService;
        this.holdService = holdService;
        this.auditService = auditService;
        this.ruleBook = ruleBook;
        this.time = time;
    }

    /** 物流员取书：登记出库馆、目的馆（任务上已有）、包裹编号。 */
    @Transactional
    public Transfer pickup(Long transferId, String packageNo, String operator) {
        Transfer t = transferRepository.findLockedById(transferId)
                .orElseThrow(() -> new NotFoundException("调拨任务不存在: " + transferId));
        if (t.getStatus() != TransferStatus.CREATED) {
            throw new IllegalStateException("调拨任务当前状态为 " + t.getStatus() + "，不能出库");
        }
        if (packageNo == null || packageNo.isBlank()) {
            throw new IllegalArgumentException("包裹编号必填");
        }
        if (transferRepository.findByPackageNo(packageNo).isPresent()) {
            throw new IllegalArgumentException("包裹编号已存在: " + packageNo);
        }
        if (t.getFromLibrary().isTempClosed()) {
            throw new BusinessRuleException(RuleBookService.R_CLOSURE,
                    "出库馆临时闭馆，暂不能出库");
        }
        t.setPackageNo(packageNo);
        t.setPickedAt(time.now());
        t.setStatus(TransferStatus.PICKED_UP);

        BookCopy copy = t.getCopy();
        copy.setStatus(CopyStatus.IN_TRANSIT);
        auditService.log("TRANSFER", t.getId(), "PICKUP",
                "出库馆=" + t.getFromLibrary().getCode() + "，目的馆=" + t.getToLibrary().getCode()
                        + "，包裹号=" + packageNo,
                RuleBookService.R_TRANSFER_SLA, operator);
        return t;
    }

    /** 到馆登记。目的馆临时闭馆时仍可入库（后台接收），取书期限按复馆日顺延。 */
    @Transactional
    public Transfer arrive(Long transferId, String operator) {
        Transfer t = transferRepository.findLockedById(transferId)
                .orElseThrow(() -> new NotFoundException("调拨任务不存在: " + transferId));
        if (t.getStatus() != TransferStatus.PICKED_UP) {
            throw new IllegalStateException("只有已出库的包裹能登记到馆，当前状态: " + t.getStatus());
        }
        t.setStatus(TransferStatus.ARRIVED);
        t.setArrivedAt(time.now());

        BookCopy copy = t.getCopy();
        copy.setCurrentLibrary(t.getToLibrary());
        auditService.log("TRANSFER", t.getId(), "ARRIVE",
                "包裹 " + t.getPackageNo() + " 到馆 " + t.getToLibrary().getCode(),
                RuleBookService.R_TRANSFER_SLA, operator);

        if (t.getHold() == null) {
            holdService.onTransferArrivedWithoutActiveHold(t);
            return t;
        }
        Hold hold = holdRepository.findLockedById(t.getHold().getId()).orElse(null);
        if (hold != null && hold.getStatus() == HoldStatus.QUEUED && hold.getReservedCopy() != null) {
            holdService.markReady(hold.getId(), operator);
        } else {
            // 预约已取消/超时：到馆回架，自动补给排队中的下一位
            auditService.log("TRANSFER", t.getId(), "SHELVE",
                    "预约已失效，包裹到馆后回架并补给队列",
                    RuleBookService.R_HOLD_PICKUP_TIMEOUT, operator);
            holdService.onTransferArrivedWithoutActiveHold(t);
        }
        return t;
    }

    /** 物流丢包：馆方承担，不向读者收费；副本 LOST，预约自动重分配。 */
    @Transactional
    public Incident reportLost(Long transferId, String note, String operator) {
        Transfer t = transferRepository.findLockedById(transferId)
                .orElseThrow(() -> new NotFoundException("调拨任务不存在: " + transferId));
        if (t.getStatus() == TransferStatus.ARRIVED || t.getStatus() == TransferStatus.LOST) {
            throw new IllegalStateException("当前状态 " + t.getStatus() + " 不能登记丢包");
        }
        t.setStatus(TransferStatus.LOST);
        t.setLostAt(time.now());
        t.setNote(note);

        BookCopy copy = t.getCopy();
        copy.setStatus(CopyStatus.LOST);

        Incident inc = new Incident();
        inc.setType(IncidentType.TRANSFER_LOST);
        inc.setCopy(copy);
        inc.setHold(t.getHold());
        inc.setDescription("包裹 " + t.getPackageNo() + " 调拨 " + t.getFromLibrary().getCode()
                + "->" + t.getToLibrary().getCode() + " 丢包：" + note);
        inc.setResolution("馆方承担损失，读者免责，预约自动重新分配副本");
        inc.setStatus("RESOLVED");
        inc.setOperator(operator);
        inc.setCreatedAt(time.now());
        inc.setResolvedAt(time.now());
        incidentRepository.save(inc);

        auditService.log("TRANSFER", t.getId(), "LOST", inc.getDescription(),
                RuleBookService.R_TRANSFER_LOST_NO_CHARGE, operator);

        if (t.getHold() != null) {
            Hold hold = t.getHold();
            notificationService.notify(hold.getPatron(), NoticeType.TRANSFER_ISSUE,
                    "调拨异常通知",
                    "您预约的《" + copy.getBook().getTitle() + "》物流丢包，馆方将尽快为您重新调拨，无需赔偿。");
            holdService.relocateAfterLoss(hold.getId(), operator);
        }
        return inc;
    }

    @Transactional(readOnly = true)
    public Transfer get(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("调拨任务不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<Transfer> all() {
        return transferRepository.findAll();
    }
}
