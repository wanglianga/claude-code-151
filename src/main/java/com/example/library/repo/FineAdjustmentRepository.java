package com.example.library.repo;

import com.example.library.domain.FineAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FineAdjustmentRepository extends JpaRepository<FineAdjustment, Long> {
    List<FineAdjustment> findByFineIdOrderByCreatedAt(Long fineId);
}
