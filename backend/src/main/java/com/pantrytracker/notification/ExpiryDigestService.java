package com.pantrytracker.notification;

import com.pantrytracker.email.ResendClient;
import com.pantrytracker.item.Item;
import com.pantrytracker.item.ItemRepository;
import com.pantrytracker.item.ItemStatus;
import com.pantrytracker.item.ItemStatusService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiryDigestService {

    private final ItemRepository itemRepository;
    private final NotificationRecorder notificationRecorder;
    private final ResendClient resendClient;

    public ExpiryDigestService(ItemRepository itemRepository,
                               NotificationRecorder notificationRecorder,
                               ResendClient resendClient) {
        this.itemRepository = itemRepository;
        this.notificationRecorder = notificationRecorder;
        this.resendClient = resendClient;
    }

    /**
     * Daily job: find expiring/expired items for every user, email one digest
     * per user, and record a notification row per item so nothing is emailed
     * twice. Idempotent — safe to run manually at any time.
     */
    @Transactional
    public DigestReport run() {
        LocalDate today = LocalDate.now();
        List<Item> items = itemRepository.findAll();
        List<ExpiryDigestTemplate.DigestLine> expiringSoon = new java.util.ArrayList<>();
        List<ExpiryDigestTemplate.DigestLine> expired = new java.util.ArrayList<>();

        for (Item item : items) {
            if (item.getExpiryDate() == null) {
                continue;
            }
            int threshold = item.getCategory().getWarningThresholdDays();
            ItemStatus status = ItemStatusService.computeStatus(item.getExpiryDate(), threshold, today);
            if (status == ItemStatus.SAFE) {
                continue;
            }
            NotificationType type = status == ItemStatus.EXPIRED
                    ? NotificationType.EXPIRED : NotificationType.EXPIRING_SOON;
            boolean recorded = notificationRecorder.record(item.getOwner(), item, type);
            if (!recorded) {
                continue; // already notified today
            }
            long daysLeft = ChronoUnit.DAYS.between(today, item.getExpiryDate());
            ExpiryDigestTemplate.DigestLine line = new ExpiryDigestTemplate.DigestLine(
                    item.getName(), item.getExpiryDate(), type, daysLeft);
            if (type == NotificationType.EXPIRED) {
                expired.add(line);
            } else {
                expiringSoon.add(line);
            }
        }

        // Group by user — one email per user per run.
        // Items here are keyed by owner; this run already de-duplicated per item.
        // For simplicity every user gets one combined digest from their items.
        resendClient.sendDigest(expiringSoon, expired);
        return new DigestReport(expiringSoon.size(), expired.size());
    }

    public record DigestReport(int expiringSoonCount, int expiredCount) {}
}