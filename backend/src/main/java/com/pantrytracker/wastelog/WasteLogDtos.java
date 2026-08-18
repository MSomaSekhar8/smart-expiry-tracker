package com.pantrytracker.wastelog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class WasteLogDtos {

    private WasteLogDtos() {}

    public record Entry(
            UUID id,
            UUID userId,
            UUID itemId,
            String itemName,
            BigDecimal quantityWasted,
            String unit,
            BigDecimal estimatedCostLost,
            Instant loggedAt) {}
}