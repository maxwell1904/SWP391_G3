-- Keep booking.paid_amount as gross collected value. Refunds are represented
-- separately by refund rows and invoice.refund_amount; they must never reduce
-- the historical receipt total.
with collected as (
    select booking_id, coalesce(sum(amount), 0)::numeric(12, 2) as gross_paid
    from payment
    where status in ('paid', 'partially_refunded', 'refunded')
    group by booking_id
)
update booking b
set paid_amount = c.gross_paid,
    remaining_amount = greatest(b.total_amount - c.gross_paid, 0)
from collected c
where c.booking_id = b.booking_id
  and (b.paid_amount is distinct from c.gross_paid
       or b.remaining_amount is distinct from greatest(b.total_amount - c.gross_paid, 0));

with completed_refunds as (
    select booking_id, coalesce(sum(refund_amount), 0)::numeric(12, 2) as refunded
    from refund
    where status = 'completed'
    group by booking_id
)
update invoice i
set paid_amount = b.paid_amount,
    remaining_amount = b.remaining_amount,
    refund_amount = coalesce(r.refunded, 0)
from booking b
left join completed_refunds r on r.booking_id = b.booking_id
where i.booking_id = b.booking_id
  and (i.paid_amount is distinct from b.paid_amount
       or i.remaining_amount is distinct from b.remaining_amount
       or i.refund_amount is distinct from coalesce(r.refunded, 0));

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'booking'::regclass
          and conname = 'chk_booking_financial_relationships'
    ) then
        alter table booking add constraint chk_booking_financial_relationships check (
            promotion_discount_amount + membership_discount_amount
                <= field_price_amount + service_total_amount
            and total_amount <= field_price_amount + service_total_amount
            and deposit_amount <= total_amount
            and remaining_amount <= total_amount
            and refundable_amount <= paid_amount
        );
    end if;

    if not exists (
        select 1 from pg_constraint
        where conrelid = 'payment'::regclass
          and conname = 'chk_payment_provider_breakdown'
    ) then
        alter table payment add constraint chk_payment_provider_breakdown check (
            provider_fee_amount is null
            or provider_net_amount is null
            or provider_fee_amount + provider_net_amount = amount
        );
    end if;
end
$$;
