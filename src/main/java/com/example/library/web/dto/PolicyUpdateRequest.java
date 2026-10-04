package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PolicyUpdateRequest(@NotBlank String key, double value) {}
