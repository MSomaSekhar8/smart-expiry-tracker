package com.pantrytracker.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    public record MonthlyPoint(
            String month,
            long totalItems,
            long wastedItems,
            BigDecimal costLost) {}

    public record MonthlyWasteResponse(
            List<MonthlyPoint> months,
            BigDecimal totalCostLost,
            long totalWasted) {}
}