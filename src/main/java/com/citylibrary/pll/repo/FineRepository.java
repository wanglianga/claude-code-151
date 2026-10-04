package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Fine;
import com.citylibrary.pll.domain.FineStatus;
import com.citylibrary.pll.domain.FineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {
    List<Fine> findByPatronIdOrderByCreatedAtDesc(Long patronId);

    Optional<Fine> findByLoanIdAndType(Long loanId, FineType type);

    @Query("""
            select coalesce(sum(f.amount), 0) from Fine f
            where f.patron.id = :patronId and f.status = :status
            """)
    BigDecimal sumOutstanding(Long patronId, FineStatus status);

    long countByStatus(FineStatus status);
}
