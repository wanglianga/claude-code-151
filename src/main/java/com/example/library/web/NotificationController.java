package com.example.library.web;

import com.example.library.service.NotificationService;
import com.example.library.web.dto.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "7. 读者通知", description = "取书通知、超时释放、逾期、取消受理、争议进度等站内信")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/readers/{cardNo}")
    @Operation(summary = "读者站内信列表")
    public List<NotificationView> forReader(@PathVariable String cardNo) {
        return notifications.viewsForReader(cardNo);
    }
}
