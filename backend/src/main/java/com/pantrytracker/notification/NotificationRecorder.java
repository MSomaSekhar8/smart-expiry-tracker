package com.pantrytracker.notification;

import com.pantrytracker.item.Item;
import com.pantrytracker.user.User;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(User user, Item item, NotificationType type) {
        try {
            notificationRepository.save(new Notification(item, user, type));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}