package com.pantrytracker.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select count(n) > 0 from Notification n
            where n.item.id = :itemId
              and n.type = :type
              and n.sentAt >= :since
            """)
    boolean existsForItemToday(@Param("itemId") UUID itemId,
                               @Param("type") NotificationType type,
                               @Param("since") Instant since);
}