package com.example.library.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 可调规则参数（借阅期限、罚款单价、保护比例等） */
@Entity
@Table(name = "policies")
@Getter
@Setter
public class Policy {
    @Id
    @Column(name = "pk", length = 48)
    private String key;

    @Column(name = "numeric_value")
    private Double numericValue;

    @Column(name = "text_value")
    private String textValue;

    private String description;
}
