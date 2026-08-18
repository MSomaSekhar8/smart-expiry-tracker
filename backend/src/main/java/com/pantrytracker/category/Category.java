package com.pantrytracker.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "default_shelf_life_days", nullable = false)
    private int defaultShelfLifeDays = 3;

    @Column(name = "warning_threshold_days", nullable = false)
    private int warningThresholdDays = 3;

    protected Category() {}

    public Category(String name, int defaultShelfLifeDays, int warningThresholdDays) {
        this.name = name;
        this.defaultShelfLifeDays = defaultShelfLifeDays;
        this.warningThresholdDays = warningThresholdDays;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDefaultShelfLifeDays() {
        return defaultShelfLifeDays;
    }

    public int getWarningThresholdDays() {
        return warningThresholdDays;
    }
}