package com.pantrytracker.common;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * The replacement for Postgres RLS: every service method that touches a
 * per-user row goes through this check before reading or mutating it.
 * A user trying to act on someone else's row gets a 403, never a 404 hint
 * about the resource's existence.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {}

    public static void requireOwner(UUID ownerId, UUID currentUserId, String resource) {
        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("You don't have access to this " + resource);
        }
    }
}