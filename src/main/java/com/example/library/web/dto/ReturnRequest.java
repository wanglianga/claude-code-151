package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnRequest(
        @NotBlank String itemBarcode,
        @NotBlank String libraryCode,
        boolean damaged,
        boolean missingPages,
        boolean accessoryMissing,
        String note
) {}
