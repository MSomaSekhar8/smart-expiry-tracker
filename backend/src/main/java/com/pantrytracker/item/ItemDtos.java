package com.pantrytracker.item;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ItemDtos {

    private ItemDtos() {}

    public record UpsertRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 200, message = "Name is too long")
            String name,

            @Size(max = 32, message = "Barcode is too long")
            String barcode,

            @NotNull(message = "Category is required")
            UUID categoryId,

            @DecimalMin(value = "0", message = "Quantity can't be negative")
            BigDecimal quantity,

            String unit,

            LocalDate purchaseDate,
            LocalDate expiryDate,
            Integer shelfLifeDays,
            String notes) {}

    public record Response(
            UUID id,
            UUID ownerId,
            String name,
            String barcode,
            UUID categoryId,
            String category,
            BigDecimal quantity,
            String unit,
            LocalDate purchaseDate,
            LocalDate expiryDate,
            Integer shelfLifeDays,
            Integer defaultShelfLifeDays,
            Integer warningThresholdDays,
            String notes,
            ItemStatus status,
            long daysUntilExpiry,
            Instant createdAt,
            Instant updatedAt) {}
}