package com.citylibrary.pll.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "book")
@Getter
@Setter
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String publisher;

    @Column(name = "publish_year")
    private Integer publishYear;

    @Column(name = "ebook_url")
    private String ebookUrl;

    @Column(name = "ebook_available", nullable = false)
    private boolean ebookAvailable;

    /** 定价：读者遗失赔偿的依据 */
    @Column(nullable = false)
    private BigDecimal price = BigDecimal.valueOf(50.00);

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
