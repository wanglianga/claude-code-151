package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "readers")
@Getter
@Setter
public class Reader {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_no", nullable = false, unique = true)
    private String cardNo;

    @Column(nullable = false)
    private String name;

    private String email;

    /** ACTIVE / SUSPENDED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";
}
