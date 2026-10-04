package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** 罚款人工调整审计：调减/豁免均留痕，记录规则依据 */
@Entity
@Table(name = "fine_adjustments")
@Getter
@Setter
public class FineAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fine_id")
    private Fine fine;

    @Column(nullable = false)
    private String staff;

    /** REDUCE / WAIVE */
    @Column(nullable = false, length = 16)
    private String action;

    @Column(name = "old_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal newAmount;

    @Column(nullable = false)
    private String reason;

    @Column(name = "rule_basis")
    private String ruleBasis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
