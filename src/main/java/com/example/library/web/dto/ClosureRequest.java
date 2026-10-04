package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ClosureRequest(
        boolean closed,
        @NotBlank String staff,
        String reason
) {}
