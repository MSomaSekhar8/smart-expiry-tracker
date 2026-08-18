package com.pantrytracker.wastelog;

import com.pantrytracker.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waste-log")
public class WasteLogController {

    private final WasteLogRepository wasteLogRepository;

    public WasteLogController(WasteLogRepository wasteLogRepository) {
        this.wasteLogRepository = wasteLogRepository;
    }

    @GetMapping
    public List<WasteLogDtos.Entry> recent(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return wasteLogRepository
                .findByUserIdOrderByLoggedAtDesc(UUID.fromString(principal.id()),
                        PageRequest.of(0, safeLimit))
                .stream()
                .map(log -> new WasteLogDtos.Entry(
                        log.getId(),
                        log.getUser().getId(),
                        log.getItem() == null ? null : log.getItem().getId(),
                        log.getItem() == null ? null : log.getItem().getName(),
                        log.getQuantityWasted(),
                        log.getItem() == null ? null : log.getItem().getUnit(),
                        log.getEstimatedCostLost(),
                        log.getLoggedAt()))
                .toList();
    }
}