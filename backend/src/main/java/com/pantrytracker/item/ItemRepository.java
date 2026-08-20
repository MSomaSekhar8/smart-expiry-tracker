package com.pantrytracker.item;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    @Query("select i from Item i where i.owner.id = :ownerId order by i.expiryDate asc nulls last")
    List<Item> findByOwnerIdOrderByExpiryDateAscNullsLast(@Param("ownerId") UUID ownerId);

    /**
     * Ownership-scoped lookup that acquires a pessimistic write lock on the
     * row (SELECT ... FOR UPDATE). Used only by markWasted: the lock serializes
     * concurrent waste requests for the same item, so only one transaction can
     * create a WasteLog and delete the item.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id and i.owner.id = :ownerId")
    Optional<Item> findOwnedForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @EntityGraph(attributePaths = {"owner", "category"})
    @Query("select i from Item i")
    List<Item> findAllWithOwnerAndCategory();

    long countByOwnerIdAndCreatedAtBetween(UUID ownerId, Instant from, Instant to);
}