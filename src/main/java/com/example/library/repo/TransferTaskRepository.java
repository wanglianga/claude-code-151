package com.example.library.repo;

import com.example.library.domain.TransferTask;
import com.example.library.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TransferTaskRepository extends JpaRepository<TransferTask, Long> {
    List<TransferTask> findByStatusOrderByCreatedAt(TaskStatus status);

    List<TransferTask> findByBatchId(Long batchId);

    Optional<TransferTask> findByPackageNo(String packageNo);

    @Query("select t from TransferTask t where t.item.id = :itemId and t.status in :statuses order by t.createdAt desc")
    List<TransferTask> findActiveForItem(Long itemId, List<TaskStatus> statuses);

    /** 统计在 Java 层完成（时间差函数在 H2/PostgreSQL 方言间不可移植） */
    @Query("select t from TransferTask t where t.pickedAt >= :since order by t.pickedAt")
    List<TransferTask> findPickedSince(java.time.Instant since);
}
