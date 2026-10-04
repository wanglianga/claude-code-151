package com.citylibrary.pll.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "book_copy")
@Getter
@Setter
public class BookCopy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    /** 馆藏条码，全局唯一，预约锁定与归还核验都以条码为准 */
    @Column(nullable = false, unique = true)
    private String barcode;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owning_library_id")
    private Library owningLibrary;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "current_library_id")
    private Library currentLibrary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_state", nullable = false)
    private CopyCondition conditionState = CopyCondition.GOOD;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
