package com.example.library.web.dto;

import java.time.Instant;

public record NotificationView(
        Long id,
        String type,
        String title,
        String message,
        Instant createdAt,
        Instant readAt
) {}
