package com.example.library.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PickupRequest(
        @NotEmpty List<Long> taskIds,
        String staffName
) {}
