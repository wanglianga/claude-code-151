package com.example.library.repo;

import com.example.library.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByReaderCardNoOrderByCreatedAtDesc(String cardNo);

    boolean existsByReaderIdAndTypeAndCreatedAtAfter(Long readerId, String type, java.time.Instant after);

    boolean existsByReaderIdAndTypeAndMessageContaining(Long readerId, String type, String snippet);
}
