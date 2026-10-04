package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Notice;
import com.citylibrary.pll.domain.NoticeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findByPatronIdOrderByCreatedAtDesc(Long patronId);

    boolean existsByTypeAndLoanId(NoticeType type, Long loanId);
}
