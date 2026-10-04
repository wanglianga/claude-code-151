package com.citylibrary.pll.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transfer")
@Getter
@Setter
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_no", nullable = false, unique = true)
    private String packageNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "copy_id")
    private BookCopy copy;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_library_id")
    private Library fromLibrary;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_library_id")
    private Library toLibrary;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hold_id")
    private Hold hold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "eta_date")
    private LocalDate etaDate;

    @Column(name = "picked_at")
    private OffsetDateTime pickedAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "lost_at")
    private OffsetDateTime lostAt;

    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
