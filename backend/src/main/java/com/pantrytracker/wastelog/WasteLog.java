package com.pantrytracker.wastelog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import com.pantrytracker.item.Item;
import com.pantrytracker.user.User;

@Entity
@Table(name = "waste_log")
public class WasteLog {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(length = 20)
    private String unit;

    @Column(name = "quantity_wasted", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityWasted;

    @Column(name = "estimated_cost_lost", precision = 10, scale = 2)
    private BigDecimal estimatedCostLost;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt = Instant.now();

    protected WasteLog() {}

    public WasteLog(User user, Item item, BigDecimal quantityWasted, BigDecimal estimatedCostLost) {
        this.user = user;
        this.item = item;
        this.itemName = item == null ? null : item.getName();
        this.unit = item == null ? null : item.getUnit();
        this.quantityWasted = quantityWasted;
        this.estimatedCostLost = estimatedCostLost;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public String getItemName() {
        return itemName;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getQuantityWasted() {
        return quantityWasted;
    }

    public BigDecimal getEstimatedCostLost() {
        return estimatedCostLost;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }
}