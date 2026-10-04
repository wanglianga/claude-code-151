package com.citylibrary.pll.repo;

import com.citylibrary.pll.domain.Incident;
import com.citylibrary.pll.domain.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatusOrderByCreatedAtDesc(String status);
    List<Incident> findByCopyIdOrderByCreatedAtDesc(Long copyId);

    boolean existsByLoanIdAndTypeAndStatus(Long loanId, IncidentType type, String status);
}
