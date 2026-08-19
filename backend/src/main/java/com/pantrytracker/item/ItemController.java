package com.pantrytracker.item;

import com.pantrytracker.auth.AuthenticatedUser;
import com.pantrytracker.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) UUID category,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(required = false) String dir) {
        return itemService.list(UUID.fromString(principal.id()), search, category, status, sort, dir);
    }

    @GetMapping("/{id}")
    public ItemDtos.Response get(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @PathVariable UUID id) {
        return itemService.get(UUID.fromString(principal.id()), id);
    }

    @PostMapping
    public ResponseEntity<ItemDtos.Response> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody ItemDtos.UpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemService.create(UUID.fromString(principal.id()), request));
    }

    @PutMapping("/{id}")
    public ItemDtos.Response update(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody ItemDtos.UpsertRequest request) {
        return itemService.update(UUID.fromString(principal.id()), id, request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable UUID id) {
        itemService.delete(UUID.fromString(principal.id()), id);
        return new ApiResponse("Item deleted");
    }

    @PostMapping("/{id}/waste")
    public ItemDtos.Response markWasted(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody(required = false) WasteRequest request) {
        BigDecimal quantity = request == null ? null : request.quantityWasted();
        BigDecimal cost = request == null ? null : request.estimatedCostLost();
        return itemService.markWasted(UUID.fromString(principal.id()), id, quantity, cost);
    }

    public record WasteRequest(
            @DecimalMin(value = "0", inclusive = false, message = "Quantity wasted must be positive")
            @Digits(integer = 8, fraction = 2, message = "Quantity has too many digits")
            BigDecimal quantityWasted,

            @DecimalMin(value = "0", message = "Estimated cost can't be negative")
            @Digits(integer = 8, fraction = 2, message = "Estimated cost has too many digits")
            BigDecimal estimatedCostLost) {}
}