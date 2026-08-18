package com.pantrytracker.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ItemStatusServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Test
    void nullExpiryIsSafe() {
        assertThat(ItemStatusService.computeStatus(null, 3, TODAY)).isEqualTo(ItemStatus.SAFE);
    }

    @Test
    void pastDateIsExpired() {
        assertThat(ItemStatusService.computeStatus(TODAY.minusDays(1), 3, TODAY))
                .isEqualTo(ItemStatus.EXPIRED);
    }

    @Test
    void expiryTodayIsExpiring() {
        assertThat(ItemStatusService.computeStatus(TODAY, 3, TODAY))
                .isEqualTo(ItemStatus.EXPIRING);
    }

    @Test
    void expiryInsideWindowIsExpiring() {
        assertThat(ItemStatusService.computeStatus(TODAY.plusDays(2), 3, TODAY))
                .isEqualTo(ItemStatus.EXPIRING);
    }

    @Test
    void expiryExactlyAtWindowEdgeIsExpiring() {
        assertThat(ItemStatusService.computeStatus(TODAY.plusDays(3), 3, TODAY))
                .isEqualTo(ItemStatus.EXPIRING);
    }

    @Test
    void expiryBeyondWindowIsSafe() {
        assertThat(ItemStatusService.computeStatus(TODAY.plusDays(4), 3, TODAY))
                .isEqualTo(ItemStatus.SAFE);
    }

    @Test
    void zeroThresholdMeansOnlyTodayIsExpiring() {
        assertThat(ItemStatusService.computeStatus(TODAY, 0, TODAY))
                .isEqualTo(ItemStatus.EXPIRING);
        assertThat(ItemStatusService.computeStatus(TODAY.plusDays(1), 0, TODAY))
                .isEqualTo(ItemStatus.SAFE);
    }

    @Test
    void publicOverloadUsesRealNow() {
        ItemStatus result = ItemStatusService.computeStatus(LocalDate.now(), 3);
        assertThat(result).isIn(ItemStatus.EXPIRING, ItemStatus.EXPIRED);
    }
}