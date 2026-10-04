package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bib_id")
    private Bib bib;

    /** 资产归属馆 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_library_id")
    private Library homeLibrary;

    /** 当前所在馆 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_library_id")
    private Library currentLibrary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemStatus status;

    /** GOOD / FAIR / DAMAGED */
    @Column(name = "cond", nullable = false, length = 16)
    private String condition = "GOOD";

    @Column(name = "missing_pages", nullable = false)
    private boolean missingPages = false;

    @Column(name = "accessories_note")
    private String accessoriesNote;
}
