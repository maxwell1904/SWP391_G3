DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'booking_restricted'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'account_locked'
    ) THEN
        ALTER TABLE app_user RENAME COLUMN booking_restricted TO account_locked;
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'booking_restricted'
    ) THEN
        UPDATE app_user
        SET account_locked = account_locked OR booking_restricted;
        ALTER TABLE app_user DROP COLUMN booking_restricted;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'account_locked'
    ) THEN
        ALTER TABLE app_user ADD COLUMN account_locked boolean;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'restriction_reason'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'lock_reason'
    ) THEN
        ALTER TABLE app_user RENAME COLUMN restriction_reason TO lock_reason;
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'restriction_reason'
    ) THEN
        UPDATE app_user
        SET lock_reason = COALESCE(lock_reason, restriction_reason);
        ALTER TABLE app_user DROP COLUMN restriction_reason;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'app_user'
          AND column_name = 'lock_reason'
    ) THEN
        ALTER TABLE app_user ADD COLUMN lock_reason varchar(255);
    END IF;
END
$$;

UPDATE app_user
SET account_locked = false
WHERE account_locked IS NULL;

UPDATE app_user
SET account_locked = true
WHERE status = 'locked';

UPDATE app_user
SET status = 'locked'
WHERE account_locked = true
  AND status <> 'locked';

ALTER TABLE app_user
    ALTER COLUMN account_locked SET DEFAULT false,
    ALTER COLUMN account_locked SET NOT NULL;
