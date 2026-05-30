-- PostgreSQL implementation notes for code-first setup.
-- Run these after the ORM has created the base tables, or translate them into
-- Flyway/Liquibase migrations when the Spring Boot project is scaffolded.

-- A slot can only have one active booking at a time. Cancelled, rejected,
-- expired, completed, and no-show bookings no longer reserve the slot.
create unique index if not exists ux_booking_active_slot
on booking (slot_id)
where status in ('pending', 'confirmed', 'checked_in');

-- Postgres does not automatically index foreign key columns. Keep common
-- lookup and join paths fast for booking screens and reports.
create index if not exists idx_app_user_role_id on app_user (role_id);
create index if not exists idx_customer_membership_customer_id on customer_membership (customer_id);
create index if not exists idx_customer_membership_level_id on customer_membership (membership_level_id);
create index if not exists idx_field_field_type_id on field (field_type_id);
create index if not exists idx_field_price_field_id on field_price (field_id);
create index if not exists idx_slot_field_id on slot (field_id);
create index if not exists idx_booking_customer_id on booking (customer_id);
create index if not exists idx_booking_staff_id on booking (staff_id);
create index if not exists idx_booking_slot_id on booking (slot_id);
create index if not exists idx_booking_service_booking_id on booking_service (booking_id);
create index if not exists idx_booking_service_extra_service_id on booking_service (extra_service_id);
create index if not exists idx_issue_reporter_id on issue (reporter_id);
create index if not exists idx_issue_booking_id on issue (booking_id);
create index if not exists idx_issue_field_id on issue (field_id);
create index if not exists idx_issue_extra_service_id on issue (extra_service_id);
create index if not exists idx_issue_assigned_staff_id on issue (assigned_staff_id);
create index if not exists idx_promotion_field_type_id on promotion (applicable_field_type_id);
create index if not exists idx_promotion_membership_level_id on promotion (applicable_membership_level_id);
create index if not exists idx_promotion_extra_service_id on promotion (applicable_extra_service_id);
create index if not exists idx_booking_promotion_booking_id on booking_promotion (booking_id);
create index if not exists idx_booking_promotion_promotion_id on booking_promotion (promotion_id);
create index if not exists idx_payment_booking_id on payment (booking_id);
create index if not exists idx_refund_booking_id on refund (booking_id);
create index if not exists idx_refund_payment_id on refund (payment_id);
create index if not exists idx_notification_user_id on notification (user_id);
create index if not exists idx_notification_booking_id on notification (booking_id);
