package com.pantrytracker.item;

import com.pantrytracker.category.Category;
import com.pantrytracker.category.CategoryRepository;
import com.pantrytracker.common.BadRequestException;
import com.pantrytracker.common.NotFoundException;
import com.pantrytracker.common.OwnershipGuard;
import com.pantrytracker.user.User;
import com.pantrytracker.user.UserRepository;
import com.pantrytracker.wastelog.WasteLog;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final WasteLogRepository wasteLogRepository;

    public ItemService(ItemRepository itemRepository,
                       CategoryRepository categoryRepository,
                       UserRepository userRepository,
                       WasteLogRepository wasteLogRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.wasteLogRepository = wasteLogRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemDtos.Response> list(UUID userId, String search, UUID categoryId,
                                        String status, String sort, String dir) {
        String searchLower = search == null ? null : search.trim().toLowerCase();
        String statusValue = status == null ? null : status.trim().toUpperCase();
        if (statusValue != null) {
            try {
                ItemStatus.valueOf(statusValue);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid status filter: " + statusValue);
            }
        }

        List<Item> items = itemRepository.findByOwnerIdOrderByExpiryDateAscNullsLast(userId)
                .stream()
                .filter(item -> searchLower == null
                        || item.getName().toLowerCase().contains(searchLower))
                .filter(item -> categoryId == null || item.getCategory().getId().equals(categoryId))
                .filter(item -> statusValue == null || computeStatus(item) == ItemStatus.valueOf(statusValue))
                .toList();

        Comparator<ItemDtos.Response> comparator = switch (sort == null ? "expiry" : sort) {
            case "name" -> Comparator.comparing(ItemDtos.Response::name, String.CASE_INSENSITIVE_ORDER);
            case "created" -> Comparator.comparing(ItemDtos.Response::createdAt);
            case "category" -> Comparator.comparing(ItemDtos.Response::category, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(
                    ItemDtos.Response::expiryDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if ("desc".equalsIgnoreCase(dir)) {
            comparator = comparator.reversed();
        }

        return items.stream().map(this::toResponse)
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ItemDtos.Response get(UUID userId, UUID itemId) {
        return toResponse(findOwned(userId, itemId));
    }

    @Transactional
    public ItemDtos.Response create(UUID userId, ItemDtos.UpsertRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found"));

        Item item = new Item(owner, category, request.name().trim());
        applyRequest(item, request);
        itemRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public ItemDtos.Response update(UUID userId, UUID itemId, ItemDtos.UpsertRequest request) {
        Item item = findOwned(userId, itemId);
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found"));
        item.setName(request.name().trim());
        item.setCategory(category);
        applyRequest(item, request);
        return toResponse(item);
    }

    private void applyRequest(Item item, ItemDtos.UpsertRequest request) {
        item.setBarcode(blankToNull(request.barcode()));
        item.setQuantity(request.quantity() == null ? BigDecimal.ONE : request.quantity());
        item.setUnit(blankToNull(request.unit()) == null ? "unit" : request.unit().trim());
        item.setPurchaseDate(request.purchaseDate());
        item.setExpiryDate(request.expiryDate());
        item.setShelfLifeDays(request.shelfLifeDays());
        item.setNotes(blankToNull(request.notes()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public void delete(UUID userId, UUID itemId) {
        Item item = findOwned(userId, itemId);
        itemRepository.delete(item);
    }

    @Transactional
    public ItemDtos.Response markWasted(UUID userId, UUID itemId,
                                        BigDecimal quantityWasted,
                                        BigDecimal estimatedCostLost) {
        // Ownership is enforced inside the locking query itself: SELECT ... FOR
        // UPDATE WHERE id = ? AND owner.id = ?. The row lock serializes
        // concurrent markWasted calls for the same item, so only one
        // transaction can snapshot the WasteLog and delete the item.
        Item item = itemRepository.findOwnedForUpdate(itemId, userId)
                .orElseThrow(() -> {
                    if (itemRepository.existsById(itemId)) {
                        throw new AccessDeniedException("You don't have access to this item");
                    }
                    return new NotFoundException("Item not found");
                });
        if (quantityWasted == null) {
            quantityWasted = item.getQuantity();
        }
        if (quantityWasted.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Quantity wasted must be positive");
        }
        if (quantityWasted.compareTo(item.getQuantity()) > 0) {
            throw new BadRequestException("Quantity wasted cannot exceed the item quantity");
        }
        WasteLog wasteLog = new WasteLog(item.getOwner(), item,
                quantityWasted, estimatedCostLost);
        // The item is about to be deleted in the same transaction. If the
        // waste log still referenced it at flush time, Hibernate would throw
        // a TransientObjectException (an insert must not reference an entity
        // scheduled for removal). The historical snapshot columns (item_name,
        // unit) are the source of truth; waste_log.item_id is nullable by
        // design (ON DELETE SET NULL), so a null reference is schema-safe.
        wasteLog.setItem(null);
        wasteLogRepository.save(wasteLog);
        itemRepository.delete(item);
        return toResponse(item);
    }

    private Item findOwned(UUID userId, UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));
        OwnershipGuard.requireOwner(item.getOwner().getId(), userId, "item");
        return item;
    }

    private ItemStatus computeStatus(Item item) {
        return ItemStatusService.computeStatus(
                item.getExpiryDate(), item.getCategory().getWarningThresholdDays());
    }

    ItemDtos.Response toResponse(Item item) {
        Category category = item.getCategory();
        int threshold = category.getWarningThresholdDays();
        ItemStatus status = computeStatus(item);
        long daysUntilExpiry = item.getExpiryDate() == null
                ? -1L
                : ChronoUnit.DAYS.between(LocalDate.now(), item.getExpiryDate());
        return new ItemDtos.Response(
                item.getId(),
                item.getOwner().getId(),
                item.getName(),
                item.getBarcode(),
                category.getId(),
                category.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.getPurchaseDate(),
                item.getExpiryDate(),
                item.getShelfLifeDays(),
                category.getDefaultShelfLifeDays(),
                threshold,
                item.getNotes(),
                status,
                daysUntilExpiry,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}