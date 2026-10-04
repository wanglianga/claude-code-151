package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "transfer_tasks")
@Getter
@Setter
public class TransferTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id")
    private Hold hold;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_library_id")
    private Library fromLibrary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_library_id")
    private Library toLibrary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private TransferBatch batch;

    @Column(name = "package_no", length = 48)
    private String packageNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    /** 到馆时不再上架给原预约者，直接进入再分配（用于取消后继续调拨的情形） */
    @Column(name = "release_on_arrival", nullable = false)
    private boolean releaseOnArrival = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "picked_at")
    private Instant pickedAt;

    private Instant eta;

    @Column(name = "arrived_at")
    private Instant arrivedAt;
}
