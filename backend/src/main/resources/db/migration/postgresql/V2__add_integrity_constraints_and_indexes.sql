-- PostgreSQL-only integrity and performance hardening.
-- Run database/supabase/preflight.sql before migrating an old Hibernate-owned
-- database so duplicate/invalid legacy rows can be corrected deliberately.

-- These columns were added to entities after the original code-first schema.
-- IF NOT EXISTS lets a previously Hibernate-created Supabase schema be
-- baselined at V1 and hardened safely by this migration.
alter table app_user add column if not exists password_reset_token varchar(16);
alter table app_user add column if not exists password_reset_sent_at timestamp;
alter table app_user add column if not exists auth_version bigint not null default 0;
alter table payment add column if not exists provider_order_id varchar(120);
alter table payment add column if not exists provider_capture_id varchar(120);
alter table payment add column if not exists provider_status varchar(50);
alter table payment add column if not exists currency varchar(10);
alter table payment add column if not exists idempotency_key varchar(120);

-- Enums are persisted as varchar by JPA. CHECK constraints preserve that
-- mapping while stopping misspelled lifecycle values from raw SQL/imports.
alter table role add constraint chk_role_status check (status in ('active', 'inactive'));
alter table app_user add constraint chk_app_user_status check (status in ('active', 'inactive', 'locked'));
alter table membership_level add constraint chk_membership_level_status check (status in ('active', 'inactive'));
alter table field_type add constraint chk_field_type_status check (status in ('active', 'inactive'));
alter table field add constraint chk_field_status check (status in ('active', 'inactive'));
alter table field_price add constraint chk_field_price_status check (status in ('active', 'inactive'));
alter table slot add constraint chk_slot_status check (status in ('available', 'blocked'));
alter table booking add constraint chk_booking_status check (status in ('pending', 'confirmed', 'checked_in', 'completed', 'cancelled', 'no_show', 'expired', 'rejected'));
alter table booking add constraint chk_booking_source check (booking_source in ('online', 'walk_in'));
alter table extra_service add constraint chk_extra_service_type check (service_type in ('rental', 'sale', 'staff_service'));
alter table extra_service add constraint chk_extra_service_status check (status in ('active', 'inactive'));
alter table issue add constraint chk_issue_status check (status in ('open', 'in_progress', 'resolved', 'rejected'));
alter table promotion add constraint chk_promotion_discount_type check (discount_type in ('percent', 'fixed_amount'));
alter table promotion add constraint chk_promotion_status check (status in ('active', 'inactive'));
alter table payment add constraint chk_payment_option check (payment_option in ('deposit', 'full', 'remaining'));
alter table payment add constraint chk_payment_method check (payment_method in ('cash', 'online_sandbox', 'paypal_sandbox', 'bank_transfer'));
alter table payment add constraint chk_payment_status check (status in ('pending', 'paid', 'failed', 'expired', 'refunded', 'partially_refunded'));
alter table refund add constraint chk_refund_status check (status in ('requested', 'approved', 'rejected', 'processing', 'completed', 'failed'));
alter table notification add constraint chk_notification_type check (notification_type in ('booking_confirmation', 'booking_reminder', 'cancellation', 'refund', 'payment', 'issue', 'system'));
alter table system_setting add constraint chk_system_setting_status check (status in ('active', 'inactive'));

-- Domain constraints that do not duplicate business workflow rules.
alter table customer_membership add constraint chk_customer_membership_completed_count check (completed_booking_count >= 0);
alter table customer_membership add constraint chk_customer_membership_date_range check (effective_to is null or effective_from is null or effective_to >= effective_from);
alter table membership_level add constraint chk_membership_required_bookings check (required_completed_bookings >= 0);
alter table membership_level add constraint chk_membership_discount_percent check (discount_percent between 0 and 100);
alter table membership_level add constraint chk_membership_display_order check (display_order >= 0);
alter table field_type add constraint chk_field_type_capacity check (player_capacity is null or player_capacity > 0);
alter table field_price add constraint chk_field_price_time_range check (end_time > start_time);
alter table field_price add constraint chk_field_price_amount check (price >= 0);
alter table field_price add constraint chk_field_price_date_range check (effective_to is null or effective_from is null or effective_to >= effective_from);
alter table slot add constraint chk_slot_time_range check (end_time > start_time);
alter table extra_service add constraint chk_extra_service_unit_price check (unit_price >= 0);
alter table extra_service add constraint chk_extra_service_stock check (stock_quantity is null or stock_quantity >= 0);
alter table extra_service add constraint chk_extra_service_max_quantity check (max_quantity_per_booking is null or max_quantity_per_booking > 0);
alter table booking_service add constraint chk_booking_service_quantity check (quantity > 0);
alter table booking_service add constraint chk_booking_service_unit_price check (unit_price >= 0);
alter table booking_service add constraint chk_booking_service_line_total check (line_total >= 0);
alter table promotion add constraint chk_promotion_discount_value check (discount_value >= 0);
alter table promotion add constraint chk_promotion_max_discount check (max_discount_amount is null or max_discount_amount >= 0);
alter table promotion add constraint chk_promotion_min_booking check (min_booking_amount is null or min_booking_amount >= 0);
alter table promotion add constraint chk_promotion_usage_limit check (usage_limit is null or usage_limit >= 0);
alter table promotion add constraint chk_promotion_used_count check (used_count >= 0 and (usage_limit is null or used_count <= usage_limit));
alter table promotion add constraint chk_promotion_date_range check (end_date >= start_date);
alter table promotion add constraint chk_promotion_time_range check (applicable_end_time is null or applicable_start_time is null or applicable_end_time > applicable_start_time);
alter table payment add constraint chk_payment_amount check (amount > 0);
alter table refund add constraint chk_refund_amount check (refund_amount > 0);
alter table invoice add constraint chk_invoice_amounts check (field_amount >= 0 and service_amount >= 0 and discount_amount >= 0 and total_amount >= 0 and paid_amount >= 0 and remaining_amount >= 0 and refund_amount >= 0);
alter table booking add constraint chk_booking_amounts check (
    field_price_amount >= 0 and service_total_amount >= 0 and promotion_discount_amount >= 0
    and membership_discount_amount >= 0 and total_amount >= 0 and deposit_amount >= 0
    and paid_amount >= 0 and remaining_amount >= 0 and cancellation_fee_amount >= 0
    and refundable_amount >= 0
);

-- Current implementation stores exactly one mutable membership state per
-- customer (the repository returns Optional, not a history list). Keep this
-- invariant until a separate membership_history model is introduced.
alter table customer_membership add constraint uk_customer_membership_customer unique (customer_id);

-- A field cannot have overlapping slots on the same date. The existing unique
-- key catches exact duplicates; this exclusion constraint catches partial
-- overlaps too (for example 18:00-20:00 vs 19:00-21:00).
create extension if not exists btree_gist;
alter table slot add constraint ex_slot_no_overlapping_time
exclude using gist (
    field_id with =,
    slot_date with =,
    tsrange(slot_date + start_time, slot_date + end_time, '[)') with &&
);

-- Only active lifecycle rows reserve a slot. This is also the concurrency
-- backstop for two simultaneous checkout requests.
create unique index ux_booking_active_slot
    on booking (slot_id)
    where status in ('pending', 'confirmed', 'checked_in');

-- PostgreSQL does not automatically index foreign keys. These cover entity
-- joins and the actual repository/report access paths without redundant
-- indexes for unique constraints whose leading columns already match.
create index idx_app_user_role_id on app_user (role_id);
create index idx_app_user_email_verification_token on app_user (email_verification_token) where email_verification_token is not null;
create index idx_app_user_password_reset_token on app_user (password_reset_token) where password_reset_token is not null;
create index idx_customer_membership_membership_level_id on customer_membership (membership_level_id);
create index idx_field_field_type_id on field (field_type_id);
create index idx_field_price_field_id on field_price (field_id);
create index idx_slot_field_id on slot (field_id);
create index idx_slot_date_status on slot (slot_date, status);
create index idx_booking_customer_id on booking (customer_id, booking_id desc);
create index idx_booking_staff_id on booking (staff_id) where staff_id is not null;
create index idx_booking_slot_id on booking (slot_id);
create index idx_booking_service_extra_service_id on booking_service (extra_service_id);
create index idx_issue_reporter_id on issue (reporter_id);
create index idx_issue_booking_id on issue (booking_id) where booking_id is not null;
create index idx_issue_field_id on issue (field_id) where field_id is not null;
create index idx_issue_extra_service_id on issue (extra_service_id) where extra_service_id is not null;
create index idx_issue_assigned_staff_id on issue (assigned_staff_id) where assigned_staff_id is not null;
create index idx_promotion_field_type_id on promotion (applicable_field_type_id) where applicable_field_type_id is not null;
create index idx_promotion_membership_level_id on promotion (applicable_membership_level_id) where applicable_membership_level_id is not null;
create index idx_promotion_extra_service_id on promotion (applicable_extra_service_id) where applicable_extra_service_id is not null;
create index idx_booking_promotion_promotion_id on booking_promotion (promotion_id);
create index idx_payment_booking_id on payment (booking_id, payment_id desc);
create index idx_payment_created_by on payment (created_by) where created_by is not null;
create index idx_refund_booking_id on refund (booking_id, refund_id desc);
create index idx_refund_payment_id on refund (payment_id) where payment_id is not null;
create index idx_refund_requested_by on refund (requested_by) where requested_by is not null;
create index idx_refund_processed_by on refund (processed_by) where processed_by is not null;
create index idx_notification_user_id on notification (user_id, created_at desc);
create index idx_notification_booking_id on notification (booking_id) where booking_id is not null;
create index idx_notification_payment_id on notification (payment_id) where payment_id is not null;
create index idx_notification_refund_id on notification (refund_id) where refund_id is not null;
create index idx_system_setting_updated_by on system_setting (updated_by) where updated_by is not null;

-- A payment-linked refund must belong to the same booking and completed (or
-- in-flight) refunds may never exceed the booking's paid amount. Locking the
-- booking row makes the aggregate check safe when two refund requests race.
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

create trigger trg_refund_integrity
before insert or update of booking_id, payment_id, refund_amount, status on refund
for each row execute function enforce_refund_integrity();
