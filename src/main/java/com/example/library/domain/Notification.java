package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id")
    private Reader reader;

    /** READY / HOLD_EXPIRED / DUE_REMINDER / OVERDUE / CANCEL / CLAIM */
    @Column(nullable = false, length = 24)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1024)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}
