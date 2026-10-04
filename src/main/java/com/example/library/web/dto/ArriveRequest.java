package com.example.library.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ArriveRequest(@NotBlank String packageNo) {}
