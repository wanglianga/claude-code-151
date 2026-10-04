package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Loan;
import com.citylibrary.pll.domain.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    Optional<Loan> findFirstByCopyIdAndStatus(Long copyId, LoanStatus status);

    List<Loan> findByPatronIdAndStatus(Long patronId, LoanStatus status);

    List<Loan> findByStatus(LoanStatus status);

    List<Loan> findByStatusAndDueDateBefore(LoanStatus status, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.copy.id = :copyId and l.status = :status")
    Optional<Loan> findLockedActiveByCopy(Long copyId, LoanStatus status);

    long countByStatus(LoanStatus status);

    long countByStatusAndDueDateBefore(LoanStatus status, LocalDate date);
}
