-- Account access previously persisted the same state in both app_user.status and
-- app_user.account_locked.  Keep app_user.status as the single source of truth;
-- the API continues to expose accountLocked as a derived compatibility field.

UPDATE app_user
SET status = 'locked'
WHERE account_locked = true
  AND status <> 'locked';

ALTER TABLE app_user
    DROP COLUMN IF EXISTS account_locked;
