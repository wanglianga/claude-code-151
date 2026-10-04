package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String readerCardNo,
        @NotBlank String itemBarcode,
        @NotBlank String libraryCode
) {}
