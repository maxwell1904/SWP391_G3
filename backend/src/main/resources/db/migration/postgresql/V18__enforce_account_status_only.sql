-- A legacy Hibernate update run could recreate this column after V16 had
-- removed it. Account access is represented only by app_user.status; keep this
-- migration as an idempotent production guard against that schema drift.

ALTER TABLE app_user
    DROP COLUMN IF EXISTS account_locked;
