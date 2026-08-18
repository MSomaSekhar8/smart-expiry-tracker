package com.pantrytracker.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pantrytracker.category.Category;
import com.pantrytracker.category.CategoryRepository;
import com.pantrytracker.common.NotFoundException;
import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        User owner = new User("user@example.com", "hash", "Test");
        ReflectionTestUtils.setField(owner, "id", userId);
        Category category = new Category("grocery", 30, 3);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        Item item = new Item(owner, category, "Oat Milk");
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
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
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.markWasted(otherUserId, item.getId(),
                BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(AccessDeniedException.class);
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
}