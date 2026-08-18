package com.pantrytracker.notification;

import com.pantrytracker.item.Item;
import com.pantrytracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 20)
    private String channel = "email";

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    protected Notification() {}

    public Notification(Item item, User user, NotificationType type) {
        this.item = item;
        this.user = user;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}