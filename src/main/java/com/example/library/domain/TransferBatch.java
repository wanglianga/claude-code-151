package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 物流批次：物流员一次取书打包形成一个批次 */
@Entity
@Table(name = "transfer_batches")
@Getter
@Setter
public class TransferBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true)
    private String batchNo;

    @Column(name = "staff_name")
    private String staffName;

    /** OPEN / CLOSED */
    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
