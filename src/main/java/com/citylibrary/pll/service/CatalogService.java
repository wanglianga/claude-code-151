package com.citylibrary.pll.service;

import com.citylibrary.pll.config.RulesProperties;
import com.citylibrary.pll.domain.*;
import com.citylibrary.pll.dto.CatalogView;
import com.citylibrary.pll.support.TimeService;
import com.citylibrary.pll.repo.BookCopyRepository;
import com.citylibrary.pll.repo.BookRepository;
import com.citylibrary.pll.repo.HoldRepository;
import com.citylibrary.pll.repo.LibraryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CatalogService {

    private static final List<HoldStatus> ACTIVE = List.of(HoldStatus.QUEUED, HoldStatus.READY);

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final BookCopyRepository copyRepository;
    private final HoldRepository holdRepository;
    private final AllocationService allocationService;
    private final RulesProperties rules;

    public CatalogService(BookRepository bookRepository, LibraryRepository libraryRepository,
                          BookCopyRepository copyRepository, HoldRepository holdRepository,
                          AllocationService allocationService, RulesProperties rules) {
        this.bookRepository = bookRepository;
        this.libraryRepository = libraryRepository;
        this.copyRepository = copyRepository;
        this.holdRepository = holdRepository;
        this.allocationService = allocationService;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<CatalogView> search(String query, String pickupLibraryCode) {
        Library pickup = libraryRepository.findByCode(pickupLibraryCode)
                .orElseThrow(() -> new IllegalArgumentException("取书馆不存在: " + pickupLibraryCode));
        List<CatalogView> views = new ArrayList<>();
        for (Book book : bookRepository.search(query)) {
            views.add(buildView(book, pickup));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public CatalogView view(Long bookId, String pickupLibraryCode) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new com.citylibrary.pll.exception.NotFoundException("书目不存在: " + bookId));
        Library pickup = libraryRepository.findByCode(pickupLibraryCode)
                .orElseThrow(() -> new IllegalArgumentException("取书馆不存在: " + pickupLibraryCode));
        return buildView(book, pickup);
    }

    private CatalogView buildView(Book book, Library pickup) {
        long localAvailable = copyRepository.countByBookIdAndCurrentLibraryIdAndStatus(
                book.getId(), pickup.getId(), CopyStatus.AVAILABLE);
        long queue = holdRepository.countActiveForBook(book.getId(), ACTIVE);
        boolean hot = queue >= rules.hotHoldThreshold();

        List<CatalogView.SourceStock> sources = new ArrayList<>();
        Integer minEta = null;
        if (localAvailable > 0) {
            sources.add(new CatalogView.SourceStock(pickup.getId(), pickup.getCode(),
                    pickup.getName(), pickup.isRemote(), localAvailable, 0, "本馆在架"));
            minEta = 0;
        }
        for (AllocationService.AllocationOption o : allocationService.options(book, pickup)) {
            if (o.local()) {
                continue;
            }
            SourceMerger.merge(sources, o);
            if (minEta == null || o.etaDays() < minEta) {
                minEta = o.etaDays();
            }
        }
        return new CatalogView(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getPrice(), book.isEbookAvailable(), book.getEbookUrl(),
                localAvailable, queue, hot, minEta, sources);
    }

    /** 同一出库馆的多个可借副本聚合成一条库存展示。 */
    static class SourceMerger {
        static void merge(List<CatalogView.SourceStock> sources, AllocationService.AllocationOption o) {
            for (int i = 0; i < sources.size(); i++) {
                CatalogView.SourceStock s = sources.get(i);
                if (s.libraryId().equals(o.source().getId())) {
                    sources.set(i, new CatalogView.SourceStock(s.libraryId(), s.libraryCode(),
                            s.libraryName(), s.remote(), s.available() + 1, s.etaDays(), s.note()));
                    return;
                }
            }
            sources.add(new CatalogView.SourceStock(o.source().getId(), o.source().getCode(),
                    o.source().getName(), o.source().isRemote(), 1, o.etaDays(), o.note()));
        }
    }
}
