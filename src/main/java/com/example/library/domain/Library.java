package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "libraries")
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
    private String region;

    private double x;
    private double y;

    /** 临时闭馆：到馆取书与还书期限顺延 */
    @Column(name = "temporary_closed", nullable = false)
    private boolean temporaryClosed = false;

    @Column(name = "closed_until")
    private Instant closedUntil;

    @Column(nullable = false)
    private boolean active = true;
}
