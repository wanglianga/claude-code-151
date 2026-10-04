package com.citylibrary.pll.service;

import com.citylibrary.pll.config.RulesProperties;
import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.BookCopyRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存分配（公平性核心）：
 * 1. 本馆有可借副本：直接从本馆取，无物流成本；
 * 2. 本馆没有：按"普通馆优先、远端馆兜底"排序候选；
 * 3. 每个出库馆必须满足本地留存比例（普通馆 25%，远端馆 50%），
 *    防止跨馆调拨抽空某馆——尤其是远端分馆；
 * 4. 临时闭馆馆不出库。
 */
@Service
public class AllocationService {

    private final BookCopyRepository copyRepository;
    private final RulesProperties rules;

    public AllocationService(BookCopyRepository copyRepository, RulesProperties rules) {
        this.copyRepository = copyRepository;
        this.rules = rules;
    }

    /** 调拨候选（用于搜索页展示与实际预约分配）。 */
    public List<AllocationOption> options(Book book, Library pickup) {
        List<AllocationOption> result = new ArrayList<>();
        for (BookCopy copy : copyRepository.findAllocatable(book.getId())) {
            Library source = copy.getCurrentLibrary();
            if (source.getId().equals(pickup.getId())) {
                result.add(new AllocationOption(copy, source, true, 0, "本馆在架"));
                continue;
            }
            if (source.isTempClosed()) {
                continue;
            }
            long totalAtSource = copyRepository.countActiveAtLibrary(book.getId(), source.getId());
            long availableAtSource = copyRepository.countByBookIdAndCurrentLibraryIdAndStatus(
                    book.getId(), source.getId(), CopyStatus.AVAILABLE);
            if (!retentionSatisfied(source, totalAtSource, availableAtSource)) {
                continue;
            }
            int eta = source.isRemote() ? rules.remoteSlaDays() : rules.normalSlaDays();
            String note = source.isRemote()
                    ? "远端分馆调拨，预计 " + eta + " 天到馆"
                    : "跨馆调拨，预计 " + eta + " 天到馆";
            result.add(new AllocationOption(copy, source, false, eta, note));
        }
        return result;
    }

    /** 为预约选出最佳副本：本馆优先，其次普通馆，最后远端馆。 */
    public AllocationOption best(Book book, Library pickup) {
        List<AllocationOption> options = options(book, pickup);
        AllocationOption best = null;
        for (AllocationOption o : options) {
            if (best == null) {
                best = o;
                continue;
            }
            boolean closer = !best.local() && o.local();
            boolean cheaperNonRemote = !best.local() && !o.local()
                    && best.source().isRemote() && !o.source().isRemote();
            boolean faster = !best.local() && !o.local()
                    && best.source().isRemote() == o.source().isRemote()
                    && o.etaDays() < best.etaDays();
            if (closer || cheaperNonRemote || faster) {
                best = o;
            }
        }
        return best;
    }

    /**
     * 留存比例校验：调拨后该馆剩余可借副本不得低于 ceil(总副本 * 留存比例)。
     *
     * @param totalCopies  该书目当前在该馆的在册副本总数（不含遗失/注销）
     * @param availableNow 其中可借（AVAILABLE）数量
     */
    public boolean retentionSatisfied(Library source, long totalCopies, long availableNow) {
        double ratio = source.isRemote() ? rules.remoteRetainRatio() : rules.localRetainRatio();
        long minKeep = (long) Math.ceil(totalCopies * ratio);
        return availableNow - 1 >= minKeep;
    }

    public record AllocationOption(BookCopy copy, Library source, boolean local,
                                   int etaDays, String note) {}
}
