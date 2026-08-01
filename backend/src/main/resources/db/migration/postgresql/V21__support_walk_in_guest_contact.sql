-- A counter booking may belong to a registered Customer or to a first-time
-- walk-in visitor. Guest contact values are a booking snapshot, not an account.
alter table booking
    alter column customer_id drop not null,
    add column if not exists guest_name varchar(120),
    add column if not exists guest_phone varchar(30),
    add column if not exists guest_email varchar(160);

alter table booking drop constraint if exists chk_booking_customer_identity;
alter table booking add constraint chk_booking_customer_identity check (
    (
        customer_id is not null
        and guest_name is null
        and guest_phone is null
        and guest_email is null
    )
    or
    (
        customer_id is null
        and booking_source = 'walk_in'
        and nullif(btrim(guest_name), '') is not null
        and nullif(btrim(guest_phone), '') is not null
    )
);

-- Reconcile only the known legacy demo labels; user-created field names remain untouched.
update field set field_name = 'Field 5A', updated_at = now() where field_name = 'Pitch A';
update field set field_name = 'Field 7A', updated_at = now() where field_name = 'Pitch B';
update field set field_name = 'Covered Field 5B', updated_at = now() where field_name = 'Pitch C';
