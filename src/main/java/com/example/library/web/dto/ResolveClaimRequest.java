package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveClaimRequest(
        @NotBlank String staff,
        @NotBlank String foundLibraryCode,
        String reason
) {}
