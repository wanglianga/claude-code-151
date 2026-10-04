package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record HoldRequest(
        @NotBlank String readerCardNo,
        @NotBlank String pickupLibraryCode
) {}
