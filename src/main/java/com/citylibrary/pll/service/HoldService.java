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
 * 预约排队服务。
 * 并发控制：以书目行锁（PESSIMISTIC_WRITE）串行化同一书目的排队与分配；
 * 副本分配后再对副本行加锁并置 HELD，杜绝一条条码分给两位读者。
 */
@Service
public class HoldService {

    public static final List<HoldStatus> ACTIVE = List.of(HoldStatus.QUEUED, HoldStatus.READY);

    private final HoldRepository holdRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final LibraryRepository libraryRepository;
    private final PatronRepository patronRepository;
    private final TransferRepository transferRepository;
    private final AllocationService allocationService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RuleBookService ruleBook;
    private final PatronBlockService blockService;
    private final TimeService time;

    public HoldService(HoldRepository holdRepository, BookRepository bookRepository,
                       BookCopyRepository copyRepository, LibraryRepository libraryRepository,
                       PatronRepository patronRepository, TransferRepository transferRepository,
                       AllocationService allocationService, NotificationService notificationService,
                       AuditService auditService, RuleBookService ruleBook,
                       PatronBlockService blockService, TimeService time) {
        this.holdRepository = holdRepository;
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.libraryRepository = libraryRepository;
        this.patronRepository = patronRepository;
        this.transferRepository = transferRepository;
        this.allocationService = allocationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.ruleBook = ruleBook;
        this.blockService = blockService;
        this.time = time;
    }

    @Transactional
    public Hold placeHold(Long bookId, String cardNo, String pickupCode, String operator) {
        Book book = bookRepository.findLockedById(bookId)
                .orElseThrow(() -> new NotFoundException("书目不存在: " + bookId));
        Patron patron = patronRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new NotFoundException("读者证不存在: " + cardNo));
        Library pickup = libraryRepository.findByCode(pickupCode)
                .orElseThrow(() -> new NotFoundException("取书馆不存在: " + pickupCode));

        blockService.checkBorrowAllowed(patron);
        if (patron.getStatus() != PatronStatus.ACTIVE) {
            throw new BusinessRuleException(RuleBookService.R_FINE_BLOCK, "读者账号已停用");
        }
        if (pickup.isTempClosed()) {
            throw new BusinessRuleException(RuleBookService.R_CLOSURE,
                    "取书馆 " + pickup.getName() + " 临时闭馆，暂不支持预约，请改选其他馆");
        }
        if (holdRepository.countByBookIdAndPatronIdAndStatusIn(bookId, patron.getId(), ACTIVE) > 0) {
            throw new BusinessRuleException(RuleBookService.R_HOLD_DUPLICATE,
                    "您已预约该书，请勿重复排队");
        }

        Hold hold = new Hold();
        hold.setBook(book);
        hold.setPatron(patron);
        hold.setPickupLibrary(pickup);
        hold.setStatus(HoldStatus.QUEUED);
        hold.setQueueSeq(holdRepository.maxQueueSeq(bookId) + 1);
        holdRepository.saveAndFlush(hold);

        auditService.log("HOLD", hold.getId(), "PLACE",
                "读者 " + cardNo + " 预约《" + book.getTitle() + "》，取书馆 " + pickup.getCode(),
                null, operator);

        fillQueue(book);
        return hold;
    }

    /**
     * 队列补给：按排队顺序为尚无锁码的预约分配副本。
     * 必须在持有书目锁的事务内调用（placeHold / 取消 / 丢包 / 到馆均如此）。
     */
    public void fillQueue(Book book) {
        List<Hold> active = holdRepository.findActiveForBookLocked(book.getId(), ACTIVE);
        for (Hold hold : active) {
            if (hold.getStatus() != HoldStatus.QUEUED || hold.getReservedCopy() != null) {
                continue;
            }
            if (hold.getPickupLibrary().isTempClosed()) {
                continue;
            }
            AllocationService.AllocationOption option =
                    allocationService.best(book, hold.getPickupLibrary());
            if (option == null) {
                break; // 队首拿不到书，后面也不跨位分配（公平：严格 FIFO）
            }
            BookCopy copy = copyRepository.findLockedById(option.copy().getId()).orElseThrow();
            if (copy.getStatus() != CopyStatus.AVAILABLE) {
                continue;
            }
            copy.setStatus(CopyStatus.HELD);
            copyRepository.saveAndFlush(copy);
            hold.setReservedCopy(copy);

            if (option.local()) {
                makeReady(hold, copy);
            } else {
                createTransfer(hold, copy, option.source(), hold.getPickupLibrary());
            }
            holdRepository.saveAndFlush(hold);
        }
    }

    private void createTransfer(Hold hold, BookCopy copy, Library from, Library to) {
        Transfer t = new Transfer();
        t.setCopy(copy);
        t.setFromLibrary(from);
        t.setToLibrary(to);
        t.setHold(hold);
        t.setStatus(TransferStatus.CREATED);
        boolean remoteLeg = from.isRemote() || to.isRemote();
        int sla = remoteLeg ? ruleBook.parameters().remoteSlaDays()
                : ruleBook.parameters().normalSlaDays();
        t.setEtaDate(time.today().plusDays(sla));
        transferRepository.save(t);
    }

    /** 到馆/本馆即取：状态置 READY，闭馆期间取书期限自复馆日起算。 */
    public void makeReady(Hold hold, BookCopy copy) {
        hold.setStatus(HoldStatus.READY);
        hold.setReadyAt(time.now());
        int pickupDays = ruleBook.parameters().holdPickupDays();
        var deadlineBase = time.today();
        Library pickup = hold.getPickupLibrary();
        if (pickup.isTempClosed() && pickup.getReopenDate() != null
                && pickup.getReopenDate().isAfter(deadlineBase)) {
            deadlineBase = pickup.getReopenDate();
        }
        hold.setExpiresAt(deadlineBase.plusDays(pickupDays).atTime(23, 59)
                .atOffset(time.now().getOffset()));
        copy.setStatus(CopyStatus.HELD);
        notificationService.notify(hold.getPatron(), NoticeType.READY_FOR_PICKUP,
                "图书已到馆，请尽快取书",
                "您预约的《" + hold.getBook().getTitle() + "》已到 " + pickup.getName()
                        + "，请在 " + pickupDays + " 天内（截至 " + deadlineBase.plusDays(pickupDays)
                        + "）取书，超时将自动释放给下一位读者。");
    }

    /** TransferService 到馆登记后回调。 */
    @Transactional
    public void markReady(Long holdId, String operator) {
        Hold hold = holdRepository.findLockedById(holdId)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + holdId));
        if (hold.getStatus() != HoldStatus.QUEUED || hold.getReservedCopy() == null) {
            throw new IllegalStateException("预约当前状态不可置为待取: " + hold.getStatus());
        }
        makeReady(hold, hold.getReservedCopy());
        auditService.log("HOLD", hold.getId(), "READY",
                "调拨到馆，等待读者取书", RuleBookService.R_HOLD_PICKUP_TIMEOUT, operator);
    }

    /** 读者/馆员取消预约。 */
    @Transactional
    public Hold cancel(Long holdId, String reason, String operator) {
        Hold hold = holdRepository.findLockedById(holdId)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + holdId));
        if (hold.getStatus() != HoldStatus.QUEUED && hold.getStatus() != HoldStatus.READY) {
            throw new IllegalStateException("仅排队中或待取的预约可取消，当前: " + hold.getStatus());
        }
        boolean byPatron = operator == null;
        doCancel(hold, reason == null ? "读者主动取消" : reason, operator == null ? "PATRON" : operator);
        notificationService.notify(hold.getPatron(), NoticeType.HOLD_CANCELLED, "预约已取消",
                "您对《" + hold.getBook().getTitle() + "》的预约已取消。");
        releaseAndRefill(hold, byPatron);
        return hold;
    }

    private void doCancel(Hold hold, String reason, String operator) {
        hold.setStatus(HoldStatus.CANCELLED);
        hold.setCancelReason(reason);
        hold.setCancelledAt(time.now());
        auditService.log("HOLD", hold.getId(), "CANCEL", reason,
                RuleBookService.R_HOLD_CANCEL, operator);
    }

    /** 取书超时释放（定时任务与手动触发共用）。返回本次释放条数。 */
    @Transactional
    public int expireReadyHolds() {
        List<Hold> due = holdRepository.findByStatusAndExpiresAtBefore(
                HoldStatus.READY, time.now());
        for (Hold hold : due) {
            Hold locked = holdRepository.findLockedById(hold.getId()).orElse(null);
            if (locked == null || locked.getStatus() != HoldStatus.READY) {
                continue;
            }
            // 闭馆顺延保护：闭馆期间不应超时
            if (locked.getPickupLibrary().isTempClosed()) {
                continue;
            }
            locked.setStatus(HoldStatus.EXPIRED);
            locked.setCancelReason("超过取书期限自动释放");
            locked.setCancelledAt(time.now());
            notificationService.notify(locked.getPatron(), NoticeType.HOLD_EXPIRED,
                    "预约已超时释放",
                    "您预约的《" + locked.getBook().getTitle()
                            + "》超过取书期限未取，已释放给下一位读者。");
            auditService.log("HOLD", locked.getId(), "EXPIRE",
                    "超过取书期限自动释放给下一位预约者",
                    RuleBookService.R_HOLD_PICKUP_TIMEOUT, "SYSTEM");
            releaseAndRefill(locked, false);
        }
        return due.size();
    }

    /**
     * 释放锁码并补给队列：
     * - 调拨尚未出库：连调拨任务一并取消，副本在出库馆回架；
     * - 调拨在途：不回架，包裹继续到目的馆，到馆后由 TransferService 回架并补给；
     * - 本馆/已到馆：副本在当前馆回架；
     * - 改派优化：若队首预约者持有尚未出库的跨馆调拨，而本馆刚释放副本，
     *   则取消其调拨、就地锁码置 READY，节省物流成本。
     */
    private void releaseAndRefill(Hold hold, boolean cancelledByPatron) {
        BookCopy copy = hold.getReservedCopy();
        if (copy == null) {
            fillQueue(hold.getBook());
            return;
        }
        List<Transfer> transfers = transferRepository.findByHoldAndStatus(hold.getId(),
                List.of(TransferStatus.CREATED, TransferStatus.PICKED_UP));
        Transfer active = transfers.isEmpty() ? null : transfers.get(0);

        if (active != null && active.getStatus() == TransferStatus.CREATED) {
            active.setStatus(TransferStatus.CANCELLED);
            active.setNote("预约取消/超时，调拨任务取消");
            copy.setStatus(CopyStatus.AVAILABLE);
        } else if (active != null && active.getStatus() == TransferStatus.PICKED_UP) {
            active.setNote("预约取消/超时，包裹继续运送，到馆后回架并补给队列");
            copy.setStatus(CopyStatus.IN_TRANSIT);
        } else {
            copy.setStatus(CopyStatus.AVAILABLE);
        }
        hold.setReservedCopy(null);

        if (copy.getStatus() == CopyStatus.AVAILABLE) {
            rerouteQueuedToLocalCopy(hold.getBook(), copy);
        }
        fillQueue(hold.getBook());
    }

    /**
     * 本馆回架副本优先改派给"已排队且跨馆调拨尚未出库"的第一位读者（严格 FIFO）。
     * 被取消调拨的源副本回架后，仍可通过 fillQueue 服务后续排队者。
     */
    private void rerouteQueuedToLocalCopy(Book book, BookCopy localCopy) {
        for (Hold h : holdRepository.findActiveForBookLocked(book.getId(), ACTIVE)) {
            if (h.getStatus() != HoldStatus.QUEUED || h.getReservedCopy() == null) {
                continue;
            }
            if (!h.getPickupLibrary().getId().equals(localCopy.getCurrentLibrary().getId())) {
                continue;
            }
            List<Transfer> created = transferRepository.findByHoldAndStatus(
                    h.getId(), List.of(TransferStatus.CREATED));
            if (created.isEmpty()) {
                break; // 队首已在途，不跳过队首改派后面的读者（FIFO）
            }
            Transfer oldTransfer = created.get(0);
            BookCopy oldCopy = oldTransfer.getCopy();
            oldTransfer.setStatus(TransferStatus.CANCELLED);
            oldTransfer.setNote("取书馆有回架副本，改派本地条码以节省物流");
            oldCopy.setStatus(CopyStatus.AVAILABLE);
            h.setReservedCopy(null);

            localCopy.setStatus(CopyStatus.HELD);
            h.setReservedCopy(localCopy);
            makeReady(h, localCopy);
            auditService.log("HOLD", h.getId(), "REROUTE_LOCAL",
                    "上位预约释放后改派本馆条码 " + localCopy.getBarcode()
                            + "，取消出库调拨 " + oldTransfer.getId(),
                    RuleBookService.R_RETAIN_RATIO, "SYSTEM");
            return;
        }
    }

    /** 物流丢包后：解除丢失副本的锁定，尝试从其他馆重新分配。 */
    @Transactional
    public void relocateAfterLoss(Long holdId, String operator) {
        Hold hold = holdRepository.findLockedById(holdId).orElse(null);
        if (hold == null || hold.getStatus() != HoldStatus.QUEUED) {
            return;
        }
        hold.setReservedCopy(null);
        holdRepository.saveAndFlush(hold);
        fillQueue(hold.getBook());
        Hold reloaded = holdRepository.findLockedById(holdId).orElse(hold);
        if (reloaded.getReservedCopy() != null) {
            notificationService.notify(reloaded.getPatron(), NoticeType.TRANSFER_ISSUE,
                    "已为您重新调拨",
                    "新的调拨任务已生成，可在预约详情中查看最新预计到馆时间。");
        } else {
            auditService.log("HOLD", holdId, "WAIT_REALLOCATION",
                    "丢包后暂无可调副本，读者继续排队等待",
                    RuleBookService.R_TRANSFER_LOST_NO_CHARGE, operator);
        }
    }

    /** 到馆时若预约已取消/超时：副本在目的馆回架并补给队列（TransferService 调用）。 */
    @Transactional
    public void onTransferArrivedWithoutActiveHold(Transfer t) {
        BookCopy copy = t.getCopy();
        copy.setStatus(CopyStatus.AVAILABLE);
        copyRepository.saveAndFlush(copy);
        fillQueue(copy.getBook());
    }

    @Transactional(readOnly = true)
    public Hold get(Long id) {
        return holdRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<Hold> patronHolds(String cardNo) {
        Patron patron = patronRepository.findByCardNo(cardNo)
                .orElseThrow(() -> new NotFoundException("读者证不存在: " + cardNo));
        return holdRepository.findByPatronIdAndStatusInOrderByCreatedAtDesc(patron.getId(),
                List.of(HoldStatus.values()));
    }
}
