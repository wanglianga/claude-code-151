package com.example.library.repo;

import com.example.library.domain.TransferBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferBatchRepository extends JpaRepository<TransferBatch, Long> {
    List<TransferBatch> findByStatusOrderByCreatedAtDesc(String status);
}
