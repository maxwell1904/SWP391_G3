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
        'chk_booking_financial_relationships',
        'chk_booking_customer_identity',
        'chk_payment_method',
        'chk_payment_provider_amounts',
        'chk_payment_provider_breakdown',
        'chk_refund_amount',
        'chk_promotion_used_count'
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
        select 1 from flyway_schema_history
        where version = '21' and success
    ) then
        raise exception 'Walk-in guest migration V21 is missing';
    end if;

    if (
        select count(*)
        from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'booking'
          and column_name in ('guest_name', 'guest_phone', 'guest_email')
    ) <> 3 then
        raise exception 'One or more walk-in guest contact columns are missing';
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'booking'
          and column_name = 'customer_id'
          and is_nullable <> 'YES'
    ) then
        raise exception 'booking.customer_id must be nullable for first-time walk-in visitors';
    end if;

    if not exists (
        select 1 from flyway_schema_history
        where version = '20' and success
    ) then
        raise exception 'Promotion usage reconciliation migration V20 is missing';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'booking_promotion'
          and column_name = 'usage_counted'
    ) then
        raise exception 'booking_promotion.usage_counted is missing';
    end if;

    if exists (
        select 1
        from booking_promotion bp
        join booking b on b.booking_id = bp.booking_id
        where bp.usage_counted is distinct from (
            bp.discount_amount > 0
            and b.status in ('pending', 'confirmed', 'checked_in', 'completed', 'no_show')
        )
    ) then
        raise exception 'booking promotion usage flags do not match qualifying booking states';
    end if;

    if exists (
        select 1
        from promotion p
        left join (
            select promotion_id, count(*)::integer as actual_count
            from booking_promotion
            where usage_counted
            group by promotion_id
        ) usage on usage.promotion_id = p.promotion_id
        where p.used_count is distinct from coalesce(usage.actual_count, 0)
    ) then
        raise exception 'promotion.used_count is not reconciled to active reservation/confirmed booking usage';
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

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'app_user'
          and column_name in ('booking_restricted', 'account_locked', 'restriction_reason')
    ) then
        raise exception 'Obsolete duplicated customer access columns still exist on app_user';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = current_schema()
          and table_name = 'app_user'
          and column_name = 'lock_reason'
    ) then
        raise exception 'app_user.lock_reason is missing';
    end if;
end;
$$;

do $$
declare
    required_slot_rules text[] := array[
        'slot.opening_time',
        'slot.closing_time',
        'slot.duration_minutes',
        'slot.generation_horizon_days'
    ];
begin
    if (
        select count(*)
        from system_setting
        where setting_key = any(required_slot_rules)
          and status = 'active'
    ) <> cardinality(required_slot_rules) then
        raise exception 'One or more automatic slot-generation rules are missing: %', required_slot_rules;
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1 from flyway_schema_history
        where version = '19' and success
    ) then
        raise exception 'Financial ledger reconciliation migration V19 is missing';
    end if;

    if exists (
        select 1
        from booking b
        left join (
            select booking_id, sum(amount)::numeric(12, 2) as gross_paid
            from payment
            where status in ('paid', 'partially_refunded', 'refunded')
            group by booking_id
        ) p on p.booking_id = b.booking_id
        where b.paid_amount is distinct from coalesce(p.gross_paid, 0)
    ) then
        raise exception 'booking.paid_amount is not reconciled to gross collected payments';
    end if;

    if exists (
        select 1 from payment
        where payment_method = 'paypal_sandbox'
          and status in ('paid', 'partially_refunded', 'refunded')
          and (provider_fee_amount is null or provider_net_amount is null)
    ) then
        raise exception 'One or more collected PayPal payments have untracked processor fees';
    end if;

    if exists (
        select 1
        from refund r
        join booking b on b.booking_id = r.booking_id
        where r.status in ('approved', 'processing', 'completed')
        group by r.booking_id, b.paid_amount
        having sum(r.refund_amount) > b.paid_amount
    ) then
        raise exception 'Committed refunds exceed gross collected payment';
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
