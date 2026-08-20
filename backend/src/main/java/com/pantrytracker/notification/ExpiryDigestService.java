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
     *
     * Flow: prepare the digest (query only) → send the email (outside any
     * transaction) → record the notification ONLY when the send succeeded.
     * A failed send is never recorded, so it is retried on the next run, and
     * one user's send failure never aborts the rest of the batch.
     */
    public DigestReport run() {
        List<DigestPlan> plans = buildPlans();

        int expiringSoonCount = 0;
        int expiredCount = 0;

        for (DigestPlan plan : plans) {
            boolean sent = resendClient.sendDigest(
                    plan.user(), plan.expiringSoon(), plan.expired());
            if (!sent) {
                continue; // not recorded → retried on a later run
            }
            for (PlannedItem planned : plan.items()) {
                if (notificationRecorder.record(plan.user(), planned.item(), planned.type())) {
                    if (planned.type() == NotificationType.EXPIRED) {
                        expiredCount++;
                    } else {
                        expiringSoonCount++;
                    }
                }
            }
        }

        return new DigestReport(expiringSoonCount, expiredCount);
    }

    /**
     * Read phase only — no email, no writes. Uses the eager query so the
     * grouped data can be used outside a transaction, and skips items that
     * were already notified today so no duplicate email is ever sent.
     */
    private List<DigestPlan> buildPlans() {
        LocalDate today = LocalDate.now();
        Map<User, List<Item>> itemsByOwner = itemRepository.findAllWithOwnerAndCategory().stream()
                .collect(Collectors.groupingBy(Item::getOwner));

        List<DigestPlan> plans = new ArrayList<>();
        for (Map.Entry<User, List<Item>> entry : itemsByOwner.entrySet()) {
            User user = entry.getKey();
            List<ExpiryDigestTemplate.DigestLine> expiringSoon = new ArrayList<>();
            List<ExpiryDigestTemplate.DigestLine> expired = new ArrayList<>();
            List<PlannedItem> items = new ArrayList<>();

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
                if (notificationRecorder.alreadyNotifiedToday(item, type)) {
                    continue; // duplicate — never emailed twice
                }
                long daysLeft = ChronoUnit.DAYS.between(today, item.getExpiryDate());
                ExpiryDigestTemplate.DigestLine line = new ExpiryDigestTemplate.DigestLine(
                        item.getName(), item.getExpiryDate(), type, daysLeft);
                if (type == NotificationType.EXPIRED) {
                    expired.add(line);
                } else {
                    expiringSoon.add(line);
                }
                items.add(new PlannedItem(item, type));
            }

            if (items.isEmpty()) {
                continue;
            }
            plans.add(new DigestPlan(user, items, expiringSoon, expired));
        }
        return plans;
    }

    private record PlannedItem(Item item, NotificationType type) {}

    private record DigestPlan(User user, List<PlannedItem> items,
                              List<ExpiryDigestTemplate.DigestLine> expiringSoon,
                              List<ExpiryDigestTemplate.DigestLine> expired) {}

    public record DigestReport(int expiringSoonCount, int expiredCount) {}
}