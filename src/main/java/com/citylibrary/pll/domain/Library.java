package com.citylibrary.pll.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "library")
@Getter
@Setter
public class Library {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String district;

    /** 远端分馆：跨馆调拨时适用更严格的本地留存比例 */
    @Column(nullable = false)
    private boolean remote;

    @Column(name = "temp_closed", nullable = false)
    private boolean tempClosed;

    @Column(name = "reopen_date")
    private LocalDate reopenDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
