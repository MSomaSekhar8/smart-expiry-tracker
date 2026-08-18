package com.pantrytracker.wastelog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WasteLogRepository extends JpaRepository<WasteLog, UUID> {

    List<WasteLog> findByUserIdOrderByLoggedAtDesc(UUID userId, Pageable pageable);

    List<WasteLog> findByUserIdAndLoggedAtBetween(UUID userId, Instant from, Instant to);
}