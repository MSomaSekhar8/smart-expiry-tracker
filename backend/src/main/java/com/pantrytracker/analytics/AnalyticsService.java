package com.pantrytracker.analytics;

import com.pantrytracker.item.Item;
import com.pantrytracker.item.ItemRepository;
import com.pantrytracker.wastelog.WasteLog;
import com.pantrytracker.wastelog.WasteLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final WasteLogRepository wasteLogRepository;
    private final ItemRepository itemRepository;

    public AnalyticsService(WasteLogRepository wasteLogRepository,
                            ItemRepository itemRepository) {
        this.wasteLogRepository = wasteLogRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.MonthlyWasteResponse monthlyWaste(UUID userId, int months) {
        int safeMonths = Math.min(Math.max(months, 1), 24);
        LocalDate today = LocalDate.now();
        List<AnalyticsDtos.MonthlyPoint> points = new ArrayList<>();

        for (int i = safeMonths - 1; i >= 0; i--) {
            YearMonth ym = YearMonth.from(today).minusMonths(i);
            LocalDate from = ym.atDay(1);
            LocalDate toExclusive = ym.plusMonths(1).atDay(1);

            List<WasteLog> logs = wasteLogRepository.findByUserIdAndLoggedAtBetween(
                    userId, toInstant(from), toInstant(toExclusive));
            long wasted = logs.size();
            BigDecimal costLost = logs.stream()
                    .map(WasteLog::getEstimatedCostLost)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long totalItems = itemRepository.countByOwnerIdAndCreatedAtBetween(
                    userId, toInstant(from), toInstant(toExclusive));

            points.add(new AnalyticsDtos.MonthlyPoint(
                    ym.toString(), totalItems, wasted, costLost));
        }

        BigDecimal totalCost = points.stream()
                .map(AnalyticsDtos.MonthlyPoint::costLost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalWasted = points.stream().mapToLong(AnalyticsDtos.MonthlyPoint::wastedItems).sum();

        return new AnalyticsDtos.MonthlyWasteResponse(points, totalCost, totalWasted);
    }

    private Instant toInstant(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}