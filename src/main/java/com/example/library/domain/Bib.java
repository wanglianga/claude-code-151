package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bibs")
@Getter
@Setter
public class Bib {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String subject;

    @Column(name = "ebook_available", nullable = false)
    private boolean ebookAvailable = false;

    @Column(name = "ebook_url")
    private String ebookUrl;
}
