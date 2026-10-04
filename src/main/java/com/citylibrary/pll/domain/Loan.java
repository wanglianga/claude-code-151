package com.citylibrary.pll.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "loan")
@Getter
@Setter
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patron_id")
    private Patron patron;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "copy_id")
    private BookCopy copy;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "checkout_library_id")
    private Library checkoutLibrary;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "renewed_count", nullable = false)
    private int renewedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "return_library_id")
    private Library returnLibrary;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
