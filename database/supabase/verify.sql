-- Read-only verification for a Flyway-managed Supabase schema.
-- Run with scripts/verify-supabase-schema.sh after the app starts with the
-- `postgres` profile.

do $$
declare
    required_indexes text[] := array[
        'ux_booking_active_slot',
        'idx_slot_date_status',
        'idx_slot_created_by',
        'idx_payment_booking_id',
        'idx_notification_user_id',
        'idx_refund_work_queue',
        'idx_issue_work_queue',
        'uk_refund_idempotency_key',
        'uk_refund_transaction_code'
    ];
    required_constraints text[] := array[
        'uk_customer_membership_customer',
        'ex_slot_no_overlapping_time',
        'chk_booking_amounts',
        'chk_payment_method',
        'chk_payment_provider_amounts',
        'chk_refund_amount'
    ];
begin
    if not exists (select 1 from information_schema.tables where table_schema = current_schema() and table_name = 'flyway_schema_history') then
        raise exception 'Flyway history is missing; start the backend once with SPRING_PROFILES_ACTIVE=postgres';
    end if;

    if (select count(*) from pg_indexes where schemaname = current_schema() and indexname = any(required_indexes)) <> cardinality(required_indexes) then
        raise exception 'One or more required indexes are missing: %', required_indexes;
    end if;

    if (select count(*) from pg_constraint where connamespace = current_schema()::regnamespace and conname = any(required_constraints)) <> cardinality(required_constraints) then
        raise exception 'One or more required constraints are missing: %', required_constraints;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = current_schema() and table_name = 'payment' and column_name = 'provider_fee_amount'
    ) or not exists (
        select 1 from information_schema.columns
        where table_schema = current_schema() and table_name = 'payment' and column_name = 'provider_net_amount'
    ) then
        raise exception 'PayPal processor fee columns are missing from payment';
    end if;
end;
$$;

select version, description, type, installed_on, success
from flyway_schema_history
order by installed_rank;

select indexname, indexdef
from pg_indexes
where schemaname = current_schema()
  and indexname in ('ux_booking_active_slot', 'idx_slot_date_status', 'idx_slot_created_by', 'idx_payment_booking_id', 'idx_notification_user_id', 'idx_refund_work_queue', 'idx_issue_work_queue', 'uk_refund_idempotency_key', 'uk_refund_transaction_code')
order by indexname;
