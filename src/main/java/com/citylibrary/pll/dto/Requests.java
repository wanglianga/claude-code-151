package com.citylibrary.pll.dto;

import jakarta.validation.constraints.NotBlank;

public class Requests {

    public record HoldRequest(@NotBlank String cardNo, @NotBlank String pickupLibrary) {}

    public record CancelRequest(String reason) {}

    public record PickupRequest(@NotBlank String packageNo) {}

    public record OperatorRequest(String operator) {}

    public record LostRequest(@NotBlank String note) {}

    public record DirectCheckoutRequest(@NotBlank String cardNo, @NotBlank String barcode,
                                        @NotBlank String library) {}

    public record HoldCheckoutRequest(@NotBlank String barcode) {}

    public record ReturnRequest(
            @NotBlank String barcode,
            @NotBlank String returnLibrary,
            String condition,          // GOOD / WORN / DAMAGED / SEVERE
            boolean missingPages,
            boolean attachmentsMissing,
            String damageNote
    ) {}

    public record ClaimRequest(@NotBlank String description) {}

    public record ResolveFoundRequest(@NotBlank String foundLibrary) {}

    public record CloseLibraryRequest(@NotBlank String reopenDate) {}

    public record WaiveRequest(@NotBlank String reason) {}

    public record AdjustRequest(java.math.BigDecimal newAmount, @NotBlank String reason) {}

    public record PayRequest() {}
}
