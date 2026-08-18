package com.pantrytracker.barcode;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_cache")
public class ProductCache {

    @Id
    @Column(length = 32)
    private String barcode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    protected ProductCache() {}

    public ProductCache(String barcode, JsonNode payload) {
        this.barcode = barcode;
        this.payload = payload;
    }

    public String getBarcode() {
        return barcode;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}