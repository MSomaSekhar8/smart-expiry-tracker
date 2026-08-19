package com.pantrytracker.notification;

import com.pantrytracker.email.ResendClient;
import com.pantrytracker.item.Item;
import com.pantrytracker.item.ItemRepository;
import com.pantrytracker.item.ItemStatus;
import com.pantrytracker.item.ItemStatusService;
import com.pantrytracker.user.User;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
     * Daily job: for every user, find that user's expiring/expired items and
     * email ONE digest to that user's address. Items are grouped by owner so
     * a user's pantry data is never mixed with another user's in the same
     * email or sent to someone else's inbox. Idempotent — safe to run
     * manually at any time.
     */
    @Transactional
    public DigestReport run() {
        LocalDate today = LocalDate.now();
        Map<User, List<Item>> itemsByOwner = itemRepository.findAll().stream()
                .collect(Collectors.groupingBy(Item::getOwner));

        int expiringSoonCount = 0;
        int expiredCount = 0;

        for (Map.Entry<User, List<Item>> entry : itemsByOwner.entrySet()) {
            User user = entry.getKey();
            List<ExpiryDigestTemplate.DigestLine> expiringSoon = new ArrayList<>();
            List<ExpiryDigestTemplate.DigestLine> expired = new ArrayList<>();

            for (Item item : entry.getValue()) {
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
                boolean recorded = notificationRecorder.record(user, item, type);
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

            if (expiringSoon.isEmpty() && expired.isEmpty()) {
                continue;
            }
            resendClient.sendDigest(user, expiringSoon, expired);
            expiringSoonCount += expiringSoon.size();
            expiredCount += expired.size();
        }

        return new DigestReport(expiringSoonCount, expiredCount);
    }

    public record DigestReport(int expiringSoonCount, int expiredCount) {}
}