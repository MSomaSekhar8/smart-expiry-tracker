package com.pantrytracker.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pantrytracker.category.Category;
import com.pantrytracker.category.CategoryRepository;
import com.pantrytracker.common.BadRequestException;
import com.pantrytracker.common.NotFoundException;
import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import com.pantrytracker.wastelog.WasteLog;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WasteLogRepository wasteLogRepository;

    private ItemService itemService;
    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemRepository, categoryRepository,
                userRepository, wasteLogRepository);
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    private Item itemOwnedBy(UUID userId) {
        return itemOwnedBy(userId, "Oat Milk", null);
    }

    private Item itemOwnedBy(UUID userId, String name, LocalDate expiry) {
        User owner = new User("user@example.com", "hash", "Test");
        ReflectionTestUtils.setField(owner, "id", userId);
        Category category = new Category("grocery", 30, 3);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        Item item = new Item(owner, category, name);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setExpiryDate(expiry);
        return item;
    }

    @Test
    void deleteOwnItemWorks() {
        Item item = itemOwnedBy(ownerId);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        itemService.delete(ownerId, item.getId());

        verify(itemRepository).delete(item);
    }

    @Test
    void deleteAnotherUsersItemThrowsAccessDenied() {
        Item item = itemOwnedBy(ownerId);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.delete(otherUserId, item.getId()))
                .isInstanceOf(AccessDeniedException.class);

        verify(itemRepository, never()).delete(any());
    }

    @Test
    void updateAnotherUsersItemThrowsAccessDenied() {
        Item item = itemOwnedBy(ownerId);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        ItemDtos.UpsertRequest request = new ItemDtos.UpsertRequest(
                "Nope", null, UUID.randomUUID(), BigDecimal.ONE, "unit",
                null, null, null, null);

        assertThatThrownBy(() -> itemService.update(otherUserId, item.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownIdThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(itemRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.delete(ownerId, missing))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void markWastedOnAnotherUsersItemThrowsAccessDenied() {
        Item item = itemOwnedBy(ownerId);
        when(itemRepository.findOwnedForUpdate(item.getId(), otherUserId))
                .thenReturn(Optional.empty());
        when(itemRepository.existsById(item.getId())).thenReturn(true);

        assertThatThrownBy(() -> itemService.markWasted(otherUserId, item.getId(),
                BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(AccessDeniedException.class);

        verify(wasteLogRepository, never()).save(any());
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void markWastedOnMissingItemThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(itemRepository.findOwnedForUpdate(missing, ownerId))
                .thenReturn(Optional.empty());
        when(itemRepository.existsById(missing)).thenReturn(false);

        assertThatThrownBy(() -> itemService.markWasted(ownerId, missing,
                BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);

        verify(wasteLogRepository, never()).save(any());
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void responseIncludesStatusAndDaysUntilExpiry() {
        Item item = itemOwnedBy(ownerId);
        item.setExpiryDate(java.time.LocalDate.now().plusDays(2));
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        ItemDtos.Response response = itemService.get(ownerId, item.getId());

        assertThat(response.status()).isEqualTo(ItemStatus.EXPIRING);
        assertThat(response.daysUntilExpiry()).isEqualTo(2L);
        assertThat(response.warningThresholdDays()).isEqualTo(3);
    }

    @Test
    void getAnotherUsersItemThrowsAccessDenied() {
        Item item = itemOwnedBy(ownerId);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.get(otherUserId, item.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listAscendingSortsExpiryOldestFirst() {
        Item later = itemOwnedBy(ownerId, "B", LocalDate.of(2026, 9, 10));
        Item sooner = itemOwnedBy(ownerId, "A", LocalDate.of(2026, 8, 20));
        when(itemRepository.findByOwnerIdOrderByExpiryDateAscNullsLast(ownerId))
                .thenReturn(List.of(later, sooner));

        List<ItemDtos.Response> result =
                itemService.list(ownerId, null, null, null, "expiry", "asc");

        assertThat(result).extracting(ItemDtos.Response::name).containsExactly("A", "B");
    }

    @Test
    void listDescendingSortsExpiryNewestFirst() {
        Item later = itemOwnedBy(ownerId, "B", LocalDate.of(2026, 9, 10));
        Item sooner = itemOwnedBy(ownerId, "A", LocalDate.of(2026, 8, 20));
        when(itemRepository.findByOwnerIdOrderByExpiryDateAscNullsLast(ownerId))
                .thenReturn(List.of(sooner, later));

        List<ItemDtos.Response> result =
                itemService.list(ownerId, null, null, null, "expiry", "desc");

        assertThat(result).extracting(ItemDtos.Response::name).containsExactly("B", "A");
    }

    @Test
    void listAscendingNameSortsAlphabetically() {
        Item zeta = itemOwnedBy(ownerId, "Zebra", LocalDate.of(2026, 9, 10));
        Item alpha = itemOwnedBy(ownerId, "Alpha", LocalDate.of(2026, 8, 20));
        when(itemRepository.findByOwnerIdOrderByExpiryDateAscNullsLast(ownerId))
                .thenReturn(List.of(zeta, alpha));

        List<ItemDtos.Response> result =
                itemService.list(ownerId, null, null, null, "name", "asc");

        assertThat(result).extracting(ItemDtos.Response::name).containsExactly("Alpha", "Zebra");
    }

    @Test
    void listDescendingNameSortsReverseAlphabetically() {
        Item zeta = itemOwnedBy(ownerId, "Zebra", LocalDate.of(2026, 9, 10));
        Item alpha = itemOwnedBy(ownerId, "Alpha", LocalDate.of(2026, 8, 20));
        when(itemRepository.findByOwnerIdOrderByExpiryDateAscNullsLast(ownerId))
                .thenReturn(List.of(zeta, alpha));

        List<ItemDtos.Response> result =
                itemService.list(ownerId, null, null, null, "name", "DESC");

        assertThat(result).extracting(ItemDtos.Response::name).containsExactly("Zebra", "Alpha");
    }

    @Test
    void listWithInvalidStatusThrowsBadRequest() {
        // Validation happens before any repository query.
        assertThatThrownBy(() -> itemService.list(ownerId, null, null, "abc123", null, null))
                .isInstanceOf(BadRequestException.class);
        verify(itemRepository, never()).findByOwnerIdOrderByExpiryDateAscNullsLast(any());
    }

    @Test
    void listFiltersToRequestedStatus() {
        Item expiring = itemOwnedBy(ownerId, "Soon", LocalDate.now().plusDays(2));
        Item safe = itemOwnedBy(ownerId, "Safe", LocalDate.now().plusDays(30));
        when(itemRepository.findByOwnerIdOrderByExpiryDateAscNullsLast(ownerId))
                .thenReturn(List.of(expiring, safe));

        List<ItemDtos.Response> result =
                itemService.list(ownerId, null, null, "EXPIRING", "expiry", "asc");

        assertThat(result).extracting(ItemDtos.Response::name).containsExactly("Soon");
    }

    @Test
    void markWastedSnapshotsItemNameAndUnitBeforeDeletion() {
        Item item = itemOwnedBy(ownerId, "Rice", null);
        item.setUnit("kg");
        item.setQuantity(new BigDecimal("5"));
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        itemService.markWasted(ownerId, item.getId(), new BigDecimal("5"), BigDecimal.ZERO);

        ArgumentCaptor<WasteLog> captor = ArgumentCaptor.forClass(WasteLog.class);
        verify(wasteLogRepository).save(captor.capture());
        WasteLog saved = captor.getValue();
        assertThat(saved.getItemName()).isEqualTo("Rice");
        assertThat(saved.getUnit()).isEqualTo("kg");
        assertThat(saved.getQuantityWasted()).isEqualByComparingTo("5");
        assertThat(saved.getUser().getId()).isEqualTo(ownerId);
        assertThat(saved.getItem()).isNull();
        verify(itemRepository).delete(item);
    }

    @Test
    void markWastedUsesLockedOwnedLookup() {
        Item item = itemOwnedBy(ownerId, "Milk", null);
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        itemService.markWasted(ownerId, item.getId(), BigDecimal.ONE, null);

        verify(itemRepository).findOwnedForUpdate(item.getId(), ownerId);
    }

    @Test
    void markWastedOwnItemSavesWasteLogAndDeletesItem() {
        Item item = itemOwnedBy(ownerId, "Milk", null);
        item.setQuantity(new BigDecimal("2"));
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        ItemDtos.Response response = itemService.markWasted(ownerId, item.getId(),
                new BigDecimal("2"), new BigDecimal("1.5"));

        ArgumentCaptor<WasteLog> captor = ArgumentCaptor.forClass(WasteLog.class);
        verify(wasteLogRepository).save(captor.capture());
        WasteLog saved = captor.getValue();
        assertThat(saved.getUser().getId()).isEqualTo(ownerId);
        assertThat(saved.getItemName()).isEqualTo("Milk");
        assertThat(saved.getUnit()).isEqualTo("unit");
        assertThat(saved.getQuantityWasted()).isEqualByComparingTo("2");
        assertThat(saved.getEstimatedCostLost()).isEqualByComparingTo("1.5");
        assertThat(saved.getItem()).isNull();
        verify(itemRepository).delete(item);
        assertThat(response.id()).isEqualTo(item.getId());
    }

    @Test
    void markWastedExactQuantitySucceeds() {
        Item item = itemOwnedBy(ownerId, "Rice", null);
        item.setQuantity(new BigDecimal("5"));
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        itemService.markWasted(ownerId, item.getId(), new BigDecimal("5"), null);

        ArgumentCaptor<WasteLog> captor = ArgumentCaptor.forClass(WasteLog.class);
        verify(wasteLogRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantityWasted()).isEqualByComparingTo("5");
        verify(itemRepository).delete(item);
    }

    @Test
    void markWastedDefaultsQuantityToItemQuantity() {
        Item item = itemOwnedBy(ownerId, "Rice", null);
        item.setQuantity(new BigDecimal("5"));
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        itemService.markWasted(ownerId, item.getId(), null, null);

        ArgumentCaptor<WasteLog> captor = ArgumentCaptor.forClass(WasteLog.class);
        verify(wasteLogRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantityWasted()).isEqualByComparingTo("5");
        verify(itemRepository).delete(item);
    }

    @Test
    void markWastedWithNonPositiveQuantityThrowsBadRequest() {
        Item item = itemOwnedBy(ownerId, "Milk", null);
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.markWasted(ownerId, item.getId(),
                BigDecimal.ZERO, null))
                .isInstanceOf(BadRequestException.class);

        verify(wasteLogRepository, never()).save(any());
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void markWastedWithNegativeQuantityThrowsBadRequest() {
        Item item = itemOwnedBy(ownerId, "Milk", null);
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.markWasted(ownerId, item.getId(),
                new BigDecimal("-1"), null))
                .isInstanceOf(BadRequestException.class);

        verify(wasteLogRepository, never()).save(any());
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void markWastedWithQuantityExceedingItemQuantityThrowsBadRequest() {
        Item item = itemOwnedBy(ownerId, "Milk", null);
        item.setQuantity(new BigDecimal("5"));
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.markWasted(ownerId, item.getId(),
                new BigDecimal("6"), null))
                .isInstanceOf(BadRequestException.class);

        verify(wasteLogRepository, never()).save(any());
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void markWastedRepeatedRequestDoesNotCreateDuplicateWasteLog() {
        Item item = itemOwnedBy(ownerId, "Milk", null);
        item.setQuantity(new BigDecimal("2"));
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item), Optional.empty());
        // The second (retried) request finds the item already deleted.
        when(itemRepository.existsById(item.getId())).thenReturn(false);

        itemService.markWasted(ownerId, item.getId(), new BigDecimal("2"), null);

        assertThatThrownBy(() -> itemService.markWasted(ownerId, item.getId(),
                new BigDecimal("2"), null))
                .isInstanceOf(NotFoundException.class);

        verify(wasteLogRepository, times(1)).save(any());
        verify(itemRepository, times(1)).delete(item);
    }

    @Test
    void markWastedFailureLeavesNoPartialState() {
        // If persisting the waste log fails, the item must NOT be deleted:
        // the method throws before delete() is reached, and @Transactional
        // would roll the whole operation back at the database level.
        Item item = itemOwnedBy(ownerId, "Milk", null);
        when(itemRepository.findOwnedForUpdate(item.getId(), ownerId))
                .thenReturn(Optional.of(item));
        doThrow(new RuntimeException("simulated DB failure"))
                .when(wasteLogRepository).save(any(WasteLog.class));

        assertThatThrownBy(() -> itemService.markWasted(ownerId, item.getId(),
                BigDecimal.ONE, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated DB failure");

        verify(itemRepository, never()).delete(any());
    }
}