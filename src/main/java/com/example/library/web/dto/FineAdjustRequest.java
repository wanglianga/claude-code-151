package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record FineAdjustRequest(
        @NotBlank String staff,
        @NotBlank String action,
        Double newAmount,
        @NotBlank String reason
) {}
