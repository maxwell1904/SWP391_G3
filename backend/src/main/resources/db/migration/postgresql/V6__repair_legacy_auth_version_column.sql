-- V5 was recorded as applied on an earlier Supabase baseline before this
-- authentication revision existed.  Keep this repair in its own migration so
-- Flyway can reconcile those databases without rewriting migration history.
-- Existing users begin at version 0, which preserves their currently issued
-- tokens until a logout, password reset/change, or account lock increments it.
alter table app_user
    add column if not exists auth_version bigint not null default 0;
