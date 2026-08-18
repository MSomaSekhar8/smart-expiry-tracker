package com.pantrytracker.analytics;

import com.pantrytracker.auth.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/monthly-waste")
    public AnalyticsDtos.MonthlyWasteResponse monthlyWaste(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "6") int months) {
        return analyticsService.monthlyWaste(UUID.fromString(principal.id()), months);
    }
}