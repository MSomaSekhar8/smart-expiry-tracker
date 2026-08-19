package com.pantrytracker.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pantrytracker.item.ItemRepository;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private WasteLogRepository wasteLogRepository;
    @Mock
    private ItemRepository itemRepository;

    private AnalyticsService analyticsService;
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(wasteLogRepository, itemRepository);
        lenient().when(wasteLogRepository.findByUserIdAndLoggedAtBetween(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(itemRepository.countByOwnerIdAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(0L);
    }

    @Test
    void monthlyWasteQueriesOnlyTheRequestedUsersData() {
        analyticsService.monthlyWaste(userId, 2);

        verify(wasteLogRepository, times(2))
                .findByUserIdAndLoggedAtBetween(eq(userId), any(), any());
        verify(wasteLogRepository, never())
                .findByUserIdAndLoggedAtBetween(eq(otherUserId), any(), any());
        verify(itemRepository, times(2))
                .countByOwnerIdAndCreatedAtBetween(eq(userId), any(), any());
        verify(itemRepository, never())
                .countByOwnerIdAndCreatedAtBetween(eq(otherUserId), any(), any());
    }

    @Test
    void monthsAreClampedToSupportedRange() {
        analyticsService.monthlyWaste(userId, 999);

        verify(wasteLogRepository, times(24))
                .findByUserIdAndLoggedAtBetween(eq(userId), any(), any());
    }
}