package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.BookCopy;
import com.citylibrary.pll.domain.CopyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    Optional<BookCopy> findByBarcode(String barcode);

    long countByBookIdAndCurrentLibraryIdAndStatus(Long bookId, Long libraryId, CopyStatus status);

    long countByBookIdAndCurrentLibraryId(Long bookId, Long libraryId);

    @Query("""
            select count(c) from BookCopy c
            where c.book.id = :bookId and c.currentLibrary.id = :libraryId
              and c.status not in (com.citylibrary.pll.domain.CopyStatus.LOST,
                                   com.citylibrary.pll.domain.CopyStatus.WITHDRAWN)
            """)
    long countActiveAtLibrary(Long bookId, Long libraryId);

    List<BookCopy> findByBookIdAndCurrentLibraryIdAndStatus(Long bookId, Long libraryId, CopyStatus status);

    /**
     * 全局可调拨库存：AVAILABLE 且所在馆未临时闭馆。
     * 远端馆排在后面，优先从普通馆调拨，降低物流成本并保护远端库存。
     */
    @Query("""
            select c from BookCopy c
            where c.book.id = :bookId
              and c.status = com.citylibrary.pll.domain.CopyStatus.AVAILABLE
              and c.currentLibrary.tempClosed = false
            order by c.currentLibrary.remote asc, c.currentLibrary.id asc, c.barcode asc
            """)
    List<BookCopy> findAllocatable(Long bookId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.barcode = :barcode")
    Optional<BookCopy> findLockedByBarcode(String barcode);
}
