package com.example.library.service;

import com.example.library.domain.Notification;
import com.example.library.domain.Reader;
import com.example.library.repo.NotificationRepository;
import com.example.library.web.Views;
import com.example.library.web.dto.NotificationView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 站内信通知（到馆取书、超时释放、逾期、取消受理等） */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final AppClock clock;

    public NotificationService(NotificationRepository notifications, AppClock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public void notify(Reader reader, String type, String title, String message) {
        Notification n = new Notification();
        n.setReader(reader);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setCreatedAt(clock.now());
        notifications.save(n);
    }

    public boolean alreadySentAfter(Long readerId, String type, Instant after) {
        return notifications.existsByReaderIdAndTypeAndCreatedAtAfter(readerId, type, after);
    }

    public boolean alreadySentWith(Long readerId, String type, String messageSnippet) {
        return notifications.existsByReaderIdAndTypeAndMessageContaining(readerId, type, messageSnippet);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> viewsForReader(String cardNo) {
        return notifications.findByReaderCardNoOrderByCreatedAtDesc(cardNo).stream()
                .map(Views::notification).toList();
    }
}
