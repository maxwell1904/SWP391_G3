-- Run before the FIRST Flyway migration against a legacy schema that was
-- created by Hibernate. This script is read-only except for transaction locks
-- acquired while each query runs; it fails with a useful message if V2 would
-- reject existing rows.

do $$
begin
    if exists (
        select 1
        from booking first_booking
        join booking second_booking
          on second_booking.slot_id = first_booking.slot_id
         and second_booking.booking_id > first_booking.booking_id
        where first_booking.status in ('pending', 'confirmed', 'checked_in')
          and second_booking.status in ('pending', 'confirmed', 'checked_in')
    ) then
        raise exception 'Preflight failed: more than one active booking reserves a slot';
    end if;

    if exists (
        select customer_id
        from customer_membership
        group by customer_id
        having count(*) > 1
    ) then
        raise exception 'Preflight failed: customer_membership contains more than one current row for a customer';
    end if;

    if exists (
        select 1
        from slot first_slot
        join slot second_slot
          on second_slot.field_id = first_slot.field_id
         and second_slot.slot_date = first_slot.slot_date
         and second_slot.slot_id > first_slot.slot_id
         and second_slot.start_time < first_slot.end_time
         and first_slot.start_time < second_slot.end_time
    ) then
        raise exception 'Preflight failed: overlapping slots exist for the same field/date';
    end if;

    if exists (
        select 1
        from refund r
        left join booking b on b.booking_id = r.booking_id
        group by r.booking_id, b.paid_amount
        having coalesce(sum(r.refund_amount) filter (where r.status in ('approved', 'processing', 'completed')), 0) > coalesce(b.paid_amount, 0)
    ) then
        raise exception 'Preflight failed: approved/processing/completed refunds exceed a booking paid amount';
    end if;

    if exists (
        select 1
        from refund r
        join payment p on p.payment_id = r.payment_id
        where p.booking_id <> r.booking_id
    ) then
        raise exception 'Preflight failed: a refund references a payment from another booking';
    end if;
end;
$$;
