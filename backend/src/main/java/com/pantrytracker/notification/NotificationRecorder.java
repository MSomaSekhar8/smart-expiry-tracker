package com.pantrytracker.notification;

import com.pantrytracker.item.Item;
import com.pantrytracker.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks an item as notified in its own REQUIRES_NEW transaction. If two
 * scheduled runs race, the second insert violates the unique index
 * (item_id, type, day) and is silently swallowed — the email is never
 * sent twice.
 */
@Component
public class NotificationRecorder {

    private final NotificationRepository notificationRepository;

    public NotificationRecorder(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Read-only pre-check used BEFORE sending the digest, so a same-day
     * duplicate is skipped without ever sending an email. The day boundary
     * matches the unique index: the UTC day of {@code sent_at}.
     */
    @Transactional(readOnly = true)
    public boolean alreadyNotifiedToday(Item item, NotificationType type) {
        Instant utcDayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return notificationRepository.existsForItemToday(item.getId(), type, utcDayStart);
    }

    /**
     * Marks the item as notified AFTER the email send succeeded. saveAndFlush
     * forces the INSERT to run inside the try/catch, so a concurrent
     * duplicate violates the unique index here and returns false instead of
     * aborting the whole digest run.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(User user, Item item, NotificationType type) {
        try {
            notificationRepository.saveAndFlush(new Notification(item, user, type));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}