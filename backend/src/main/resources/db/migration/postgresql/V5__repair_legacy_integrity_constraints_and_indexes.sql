-- Some existing Supabase projects were baselined while the V2 hardening
-- migration was still being developed.  Their Flyway history records V2, but
-- the constraints and indexes added later to that migration are absent.  This
-- idempotent repair gives those databases the same integrity guarantees as a
-- fresh installation without changing business data.
--
-- Run database/supabase/preflight.sql before applying this migration to a
-- legacy database.  It checks the cross-row conditions that cannot be
-- expressed by a simple CHECK constraint.

alter table app_user add column if not exists password_reset_token varchar(16);
alter table app_user add column if not exists password_reset_sent_at timestamp;
alter table app_user add column if not exists auth_version bigint not null default 0;
alter table payment add column if not exists provider_order_id varchar(120);
alter table payment add column if not exists provider_capture_id varchar(120);
alter table payment add column if not exists provider_status varchar(50);
alter table payment add column if not exists currency varchar(10);
alter table payment add column if not exists idempotency_key varchar(120);

create or replace function goalzone_add_constraint_if_missing(
    target_table regclass,
    target_name text,
    definition text
)
returns void
language plpgsql
as $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = target_table
          and conname = target_name
    ) then
        execute format('alter table %s add constraint %I %s', target_table, target_name, definition);
    end if;
end;
$$;

select goalzone_add_constraint_if_missing('role', 'chk_role_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('app_user', 'chk_app_user_status', $rule$check (status in ('active', 'inactive', 'locked'))$rule$);
select goalzone_add_constraint_if_missing('membership_level', 'chk_membership_level_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('field_type', 'chk_field_type_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('field', 'chk_field_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('field_price', 'chk_field_price_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('slot', 'chk_slot_status', $rule$check (status in ('available', 'blocked'))$rule$);
select goalzone_add_constraint_if_missing('booking', 'chk_booking_status', $rule$check (status in ('pending', 'confirmed', 'checked_in', 'completed', 'cancelled', 'no_show', 'expired', 'rejected'))$rule$);
select goalzone_add_constraint_if_missing('booking', 'chk_booking_source', $rule$check (booking_source in ('online', 'walk_in'))$rule$);
select goalzone_add_constraint_if_missing('extra_service', 'chk_extra_service_type', $rule$check (service_type in ('rental', 'sale', 'staff_service'))$rule$);
select goalzone_add_constraint_if_missing('extra_service', 'chk_extra_service_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('issue', 'chk_issue_status', $rule$check (status in ('open', 'in_progress', 'resolved', 'rejected'))$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_discount_type', $rule$check (discount_type in ('percent', 'fixed_amount'))$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_status', $rule$check (status in ('active', 'inactive'))$rule$);
select goalzone_add_constraint_if_missing('payment', 'chk_payment_option', $rule$check (payment_option in ('deposit', 'full', 'remaining'))$rule$);
select goalzone_add_constraint_if_missing('payment', 'chk_payment_method', $rule$check (payment_method in ('cash', 'online_sandbox', 'paypal_sandbox', 'bank_transfer'))$rule$);
select goalzone_add_constraint_if_missing('payment', 'chk_payment_status', $rule$check (status in ('pending', 'paid', 'failed', 'expired', 'refunded', 'partially_refunded'))$rule$);
select goalzone_add_constraint_if_missing('refund', 'chk_refund_status', $rule$check (status in ('requested', 'approved', 'rejected', 'processing', 'completed', 'failed'))$rule$);
select goalzone_add_constraint_if_missing('notification', 'chk_notification_type', $rule$check (notification_type in ('booking_confirmation', 'booking_reminder', 'cancellation', 'refund', 'payment', 'issue', 'system'))$rule$);
select goalzone_add_constraint_if_missing('system_setting', 'chk_system_setting_status', $rule$check (status in ('active', 'inactive'))$rule$);

select goalzone_add_constraint_if_missing('customer_membership', 'chk_customer_membership_completed_count', $rule$check (completed_booking_count >= 0)$rule$);
select goalzone_add_constraint_if_missing('customer_membership', 'chk_customer_membership_date_range', $rule$check (effective_to is null or effective_from is null or effective_to >= effective_from)$rule$);
select goalzone_add_constraint_if_missing('membership_level', 'chk_membership_required_bookings', $rule$check (required_completed_bookings >= 0)$rule$);
select goalzone_add_constraint_if_missing('membership_level', 'chk_membership_discount_percent', $rule$check (discount_percent between 0 and 100)$rule$);
select goalzone_add_constraint_if_missing('membership_level', 'chk_membership_display_order', $rule$check (display_order >= 0)$rule$);
select goalzone_add_constraint_if_missing('field_type', 'chk_field_type_capacity', $rule$check (player_capacity is null or player_capacity > 0)$rule$);
select goalzone_add_constraint_if_missing('field_price', 'chk_field_price_time_range', $rule$check (end_time > start_time)$rule$);
select goalzone_add_constraint_if_missing('field_price', 'chk_field_price_amount', $rule$check (price >= 0)$rule$);
select goalzone_add_constraint_if_missing('field_price', 'chk_field_price_date_range', $rule$check (effective_to is null or effective_from is null or effective_to >= effective_from)$rule$);
select goalzone_add_constraint_if_missing('slot', 'chk_slot_time_range', $rule$check (end_time > start_time)$rule$);
select goalzone_add_constraint_if_missing('extra_service', 'chk_extra_service_unit_price', $rule$check (unit_price >= 0)$rule$);
select goalzone_add_constraint_if_missing('extra_service', 'chk_extra_service_stock', $rule$check (stock_quantity is null or stock_quantity >= 0)$rule$);
select goalzone_add_constraint_if_missing('extra_service', 'chk_extra_service_max_quantity', $rule$check (max_quantity_per_booking is null or max_quantity_per_booking > 0)$rule$);
select goalzone_add_constraint_if_missing('booking_service', 'chk_booking_service_quantity', $rule$check (quantity > 0)$rule$);
select goalzone_add_constraint_if_missing('booking_service', 'chk_booking_service_unit_price', $rule$check (unit_price >= 0)$rule$);
select goalzone_add_constraint_if_missing('booking_service', 'chk_booking_service_line_total', $rule$check (line_total >= 0)$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_discount_value', $rule$check (discount_value >= 0)$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_max_discount', $rule$check (max_discount_amount is null or max_discount_amount >= 0)$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_min_booking', $rule$check (min_booking_amount is null or min_booking_amount >= 0)$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_usage_limit', $rule$check (usage_limit is null or usage_limit >= 0)$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_used_count', $rule$check (used_count >= 0 and (usage_limit is null or used_count <= usage_limit))$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_date_range', $rule$check (end_date >= start_date)$rule$);
select goalzone_add_constraint_if_missing('promotion', 'chk_promotion_time_range', $rule$check (applicable_end_time is null or applicable_start_time is null or applicable_end_time > applicable_start_time)$rule$);
select goalzone_add_constraint_if_missing('payment', 'chk_payment_amount', $rule$check (amount > 0)$rule$);
select goalzone_add_constraint_if_missing('refund', 'chk_refund_amount', $rule$check (refund_amount > 0)$rule$);
select goalzone_add_constraint_if_missing('invoice', 'chk_invoice_amounts', $rule$check (field_amount >= 0 and service_amount >= 0 and discount_amount >= 0 and total_amount >= 0 and paid_amount >= 0 and remaining_amount >= 0 and refund_amount >= 0)$rule$);
select goalzone_add_constraint_if_missing('booking', 'chk_booking_amounts', $rule$check (field_price_amount >= 0 and service_total_amount >= 0 and promotion_discount_amount >= 0 and membership_discount_amount >= 0 and total_amount >= 0 and deposit_amount >= 0 and paid_amount >= 0 and remaining_amount >= 0 and cancellation_fee_amount >= 0 and refundable_amount >= 0)$rule$);
select goalzone_add_constraint_if_missing('customer_membership', 'uk_customer_membership_customer', $rule$unique (customer_id)$rule$);

create extension if not exists btree_gist;
select goalzone_add_constraint_if_missing('slot', 'ex_slot_no_overlapping_time', $rule$exclude using gist (field_id with =, slot_date with =, tsrange(slot_date + start_time, slot_date + end_time, '[)') with &&)$rule$);

create unique index if not exists ux_booking_active_slot on booking (slot_id) where status in ('pending', 'confirmed', 'checked_in');
create index if not exists idx_app_user_role_id on app_user (role_id);
create index if not exists idx_app_user_email_verification_token on app_user (email_verification_token) where email_verification_token is not null;
create index if not exists idx_app_user_password_reset_token on app_user (password_reset_token) where password_reset_token is not null;
create index if not exists idx_customer_membership_membership_level_id on customer_membership (membership_level_id);
create index if not exists idx_field_field_type_id on field (field_type_id);
create index if not exists idx_field_price_field_id on field_price (field_id);
create index if not exists idx_slot_field_id on slot (field_id);
create index if not exists idx_slot_date_status on slot (slot_date, status);
create index if not exists idx_slot_created_by on slot (created_by) where created_by is not null;
create index if not exists idx_booking_customer_id on booking (customer_id, booking_id desc);
create index if not exists idx_booking_staff_id on booking (staff_id) where staff_id is not null;
create index if not exists idx_booking_slot_id on booking (slot_id);
create index if not exists idx_booking_service_extra_service_id on booking_service (extra_service_id);
create index if not exists idx_issue_reporter_id on issue (reporter_id);
create index if not exists idx_issue_booking_id on issue (booking_id) where booking_id is not null;
create index if not exists idx_issue_field_id on issue (field_id) where field_id is not null;
create index if not exists idx_issue_extra_service_id on issue (extra_service_id) where extra_service_id is not null;
create index if not exists idx_issue_assigned_staff_id on issue (assigned_staff_id) where assigned_staff_id is not null;
create index if not exists idx_promotion_field_type_id on promotion (applicable_field_type_id) where applicable_field_type_id is not null;
create index if not exists idx_promotion_membership_level_id on promotion (applicable_membership_level_id) where applicable_membership_level_id is not null;
create index if not exists idx_promotion_extra_service_id on promotion (applicable_extra_service_id) where applicable_extra_service_id is not null;
create index if not exists idx_booking_promotion_promotion_id on booking_promotion (promotion_id);
create index if not exists idx_payment_booking_id on payment (booking_id, payment_id desc);
create index if not exists idx_payment_created_by on payment (created_by) where created_by is not null;
create index if not exists idx_refund_booking_id on refund (booking_id, refund_id desc);
create index if not exists idx_refund_payment_id on refund (payment_id) where payment_id is not null;
create index if not exists idx_refund_requested_by on refund (requested_by) where requested_by is not null;
create index if not exists idx_refund_processed_by on refund (processed_by) where processed_by is not null;
create index if not exists idx_notification_user_id on notification (user_id, created_at desc);
create index if not exists idx_notification_booking_id on notification (booking_id) where booking_id is not null;
create index if not exists idx_notification_payment_id on notification (payment_id) where payment_id is not null;
create index if not exists idx_notification_refund_id on notification (refund_id) where refund_id is not null;
create index if not exists idx_system_setting_updated_by on system_setting (updated_by) where updated_by is not null;

create or replace function enforce_refund_integrity()
returns trigger
language plpgsql
as $$
declare
    paid numeric(12,2);
    already_reserved numeric(12,2);
    payment_booking_id bigint;
begin
    if new.payment_id is not null then
        select booking_id into payment_booking_id from payment where payment_id = new.payment_id;
        if payment_booking_id is null or payment_booking_id <> new.booking_id then
            raise exception 'refund payment must belong to the refund booking';
        end if;
    end if;

    if new.status not in ('approved', 'processing', 'completed') then
        return new;
    end if;

    select paid_amount into paid from booking where booking_id = new.booking_id for update;
    if paid is null then
        raise exception 'refund booking % does not exist', new.booking_id;
    end if;

    select coalesce(sum(refund_amount), 0) into already_reserved
    from refund
    where booking_id = new.booking_id
      and refund_id is distinct from new.refund_id
      and status in ('approved', 'processing', 'completed');

    if already_reserved + new.refund_amount > paid then
        raise exception 'refund total (%) exceeds paid amount (%) for booking %',
            already_reserved + new.refund_amount, paid, new.booking_id;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_refund_integrity on refund;
create trigger trg_refund_integrity
before insert or update of booking_id, payment_id, refund_amount, status on refund
for each row execute function enforce_refund_integrity();

drop function goalzone_add_constraint_if_missing(regclass, text, text);
