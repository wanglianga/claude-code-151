package com.example.library.repo;

import com.example.library.domain.Loan;
import com.example.library.domain.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    Optional<Loan> findFirstByItemIdAndStatusOrderByCheckedOutAtDesc(Long itemId, LoanStatus status);

    List<Loan> findByReaderCardNoAndStatusOrderByCheckedOutAtDesc(String cardNo, LoanStatus status);

    @Query("select l from Loan l where l.status in :statuses")
    List<Loan> findByStatusIn(List<LoanStatus> statuses);

    @Query("select l from Loan l where l.status = :status and l.dueDate < :now")
    List<Loan> findOverdue(LoanStatus status, java.time.Instant now);

    @Query("select count(l) from Loan l where l.reader.id = :readerId and l.status = :status and l.dueDate < :now")
    long countOverdueForReader(Long readerId, LoanStatus status, java.time.Instant now);

    @Query("select l from Loan l where l.checkoutLibrary.id = :libraryId and l.status in :statuses")
    List<Loan> findActiveAtCheckoutLibrary(Long libraryId, List<LoanStatus> statuses);
}
