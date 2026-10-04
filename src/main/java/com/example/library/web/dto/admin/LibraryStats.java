package com.example.library.web.dto.admin;

public record LibraryStats(
        String code,
        String name,
        long availableCopies,
        long outgoingInTransit,
        long lostTasks,
        long activeHoldsAtPickup,
        long overdueLoans,
        long outgoingTasks,
        long incomingTasks,
        double avgOutgoingHours,
        double avgIncomingHours
) {}
