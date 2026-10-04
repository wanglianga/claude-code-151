package com.citylibrary.pll.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "hold")
@Getter
@Setter
public class Hold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patron_id")
    private Patron patron;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pickup_library_id")
    private Library pickupLibrary;

    /** 锁定的馆藏条码；排队等待时为空，一旦分配即锁定 */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reserved_copy_id")
    private BookCopy reservedCopy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldStatus status;

    @Column(name = "queue_seq", nullable = false)
    private long queueSeq;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fulfilled_loan_id")
    private Loan fulfilledLoan;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
}
