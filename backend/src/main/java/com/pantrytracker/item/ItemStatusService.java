package com.pantrytracker.item;

import java.time.LocalDate;

/**
 * Pure, testable function for the status rules:
 *   expiry date in the past            -> EXPIRED
 *   expiry within the warning window   -> EXPIRING
 *   otherwise (or no expiry date)      -> SAFE
 */
public final class ItemStatusService {

    private ItemStatusService() {}

    public static ItemStatus computeStatus(LocalDate expiryDate, int warningThresholdDays) {
        return computeStatus(expiryDate, warningThresholdDays, LocalDate.now());
    }

    /** Public overload so callers (tests, digest job) can pin "today". */
    public static ItemStatus computeStatus(LocalDate expiryDate, int warningThresholdDays, LocalDate today) {
        if (expiryDate == null) {
            return ItemStatus.SAFE;
        }
        if (expiryDate.isBefore(today)) {
            return ItemStatus.EXPIRED;
        }
        if (!expiryDate.isAfter(today.plusDays(warningThresholdDays))) {
            return ItemStatus.EXPIRING;
        }
        return ItemStatus.SAFE;
    }
}