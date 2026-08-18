package com.pantrytracker.item;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    @Query("select i from Item i where i.owner.id = :ownerId order by i.expiryDate asc nulls last")
    List<Item> findByOwnerIdOrderByExpiryDateAscNullsLast(@Param("ownerId") UUID ownerId);

    long countByOwnerIdAndCreatedAtBetween(UUID ownerId, Instant from, Instant to);
}