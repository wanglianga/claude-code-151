package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Hold;
import com.citylibrary.pll.domain.HoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    /** 某书目活跃预约（排队 + 待取），按排队序号排序，加锁防并发抢码 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from Hold h
            where h.book.id = :bookId and h.status in :statuses
            order by h.queueSeq
            """)
    List<Hold> findActiveForBookLocked(Long bookId, List<HoldStatus> statuses);

    @Query("""
            select count(h) from Hold h
            where h.book.id = :bookId and h.status in :statuses
            """)
    long countActiveForBook(Long bookId, List<HoldStatus> statuses);

    @Query("select coalesce(max(h.queueSeq), 0) from Hold h where h.book.id = :bookId")
    long maxQueueSeq(Long bookId);

    long countByBookIdAndPatronIdAndStatusIn(Long bookId, Long patronId, List<HoldStatus> statuses);

    /** 某书目在指定取书馆的活跃预约数：用于阻止本馆现场借绕过本馆排队。 */
    @Query("""
            select count(h) from Hold h
            where h.book.id = :bookId and h.pickupLibrary.id = :libraryId and h.status in :statuses
            """)
    long countActiveAtPickup(Long bookId, Long libraryId, List<HoldStatus> statuses);

    List<Hold> findByStatusAndPickupLibraryId(HoldStatus status, Long libraryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findLockedById(Long id);

    /** 取书超时：READY 且已过截止时间 */
    List<Hold> findByStatusAndExpiresAtBefore(HoldStatus status, OffsetDateTime now);

    List<Hold> findByPatronIdAndStatusInOrderByCreatedAtDesc(Long patronId, List<HoldStatus> statuses);

    Optional<Hold> findByReservedCopyIdAndStatusIn(Long copyId, List<HoldStatus> statuses);
}
