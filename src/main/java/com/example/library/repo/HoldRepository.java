package com.example.library.repo;

import com.example.library.domain.Hold;
import com.example.library.domain.HoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> lockById(Long id);

    /** 某书目活跃预约（排队 + 待取），按队列位排序；对预约分配加行锁防止两人同时抢一本 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.bib.id = :bibId and h.status in :statuses order by h.queuePosition")
    List<Hold> lockActiveByBib(Long bibId, List<HoldStatus> statuses);

    @Query("select h from Hold h where h.bib.id = :bibId and h.status in :statuses order by h.queuePosition")
    List<Hold> findActiveByBib(Long bibId, List<HoldStatus> statuses);

    @Query("select count(h) from Hold h where h.reader.id = :readerId and h.status in :statuses")
    long countActiveForReader(Long readerId, List<HoldStatus> statuses);

    @Query("select h from Hold h where h.reader.cardNo = :cardNo and h.status in :statuses order by h.createdAt desc")
    List<Hold> findActiveByReaderCard(String cardNo, List<HoldStatus> statuses);

    @Query("select h from Hold h where h.status = :status and h.expiresAt < :now")
    List<Hold> findExpired(HoldStatus status, java.time.Instant now);

    @Query("select h from Hold h where h.pickupLibrary.id = :libraryId and h.status in :statuses")
    List<Hold> findActiveAtPickup(Long libraryId, List<HoldStatus> statuses);

    /** 热门预约排行 */
    @Query("""
            select h.bib.id, h.bib.title, count(h) as c
            from Hold h
            where h.createdAt >= :since
            group by h.bib.id, h.bib.title
            order by c desc
            """)
    List<Object[]> countPopularSince(java.time.Instant since);
}
