package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Transfer;
import com.citylibrary.pll.domain.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByPackageNo(String packageNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> findLockedById(Long id);

    List<Transfer> findByStatus(TransferStatus status);

    @Query("""
            select t from Transfer t
            where t.copy.id = :copyId and t.status in :statuses
            order by t.id desc
            """)
    List<Transfer> findByCopyAndStatus(Long copyId, List<TransferStatus> statuses);

    @Query("""
            select t from Transfer t
            where t.hold.id = :holdId and t.status in :statuses
            order by t.id desc
            """)
    List<Transfer> findByHoldAndStatus(Long holdId, List<TransferStatus> statuses);

    long countByStatus(TransferStatus status);
}
