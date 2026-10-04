package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffActionRequest(
        @NotBlank String staff,
        String reason,
        String libraryCode
) {}
