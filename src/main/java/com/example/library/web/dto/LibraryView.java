package com.example.library.web.dto;

public record LibraryView(
        Long id,
        String code,
        String name,
        String region,
        boolean temporaryClosed,
        boolean active
) {}
