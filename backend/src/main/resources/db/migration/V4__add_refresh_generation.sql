-- ============================================================================
-- V4__add_refresh_generation.sql — refresh-token rotation counter
-- Each successful /api/auth/refresh increments the user's generation. Refresh
-- JWTs embed the generation they were issued at, so an old token is rejected
-- as soon as a newer one has been used (stateless rotation). Logout also
-- increments it, revoking every outstanding refresh token for that user.
-- ============================================================================

alter table users
    add column refresh_generation bigint not null default 0;