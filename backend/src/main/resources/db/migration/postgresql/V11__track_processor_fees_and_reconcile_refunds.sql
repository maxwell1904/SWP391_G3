<<<<<<< HEAD
alter table payment
    add column if not exists provider_fee_amount numeric(12, 2),
    add column if not exists provider_net_amount numeric(12, 2);

create index if not exists idx_refund_work_queue
    on refund (status, requested_at, refund_id)
    where status in ('requested', 'approved', 'processing', 'failed');
=======
alter table payment add column if not exists provider_fee_amount numeric(12,2);
alter table payment add column if not exists provider_net_amount numeric(12,2);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'chk_payment_provider_amounts') then
        alter table payment add constraint chk_payment_provider_amounts check (
            (provider_fee_amount is null or provider_fee_amount >= 0)
            and (provider_net_amount is null or provider_net_amount >= 0)
        );
    end if;
end
$$;

-- Cash has no gateway fee. Historical PayPal rows stay null until the exact
-- processor breakdown is fetched, avoiding an invented percentage.
update payment
set provider_fee_amount = 0,
    provider_net_amount = case when status in ('paid','partially_refunded','refunded') then amount else 0 end
where payment_method = 'cash'
  and (provider_fee_amount is null or provider_net_amount is null);

-- Earlier releases recorded successful refunds but did not always close the
-- remaining refundable balance or derive the payment's refund state.
with completed_by_booking as (
    select booking_id, sum(refund_amount) as refunded
    from refund
    where status = 'completed'
    group by booking_id
)
update booking b
set refundable_amount = greatest(0, b.refundable_amount - c.refunded)
from completed_by_booking c
where b.booking_id = c.booking_id
  and b.refundable_amount > 0;

with completed_by_booking as (
    select booking_id, sum(refund_amount) as refunded
    from refund
    where status = 'completed'
    group by booking_id
)
update invoice i
set refund_amount = c.refunded
from completed_by_booking c
where i.booking_id = c.booking_id
  and i.refund_amount is distinct from c.refunded;

with completed_by_payment as (
    select payment_id, sum(refund_amount) as refunded
    from refund
    where status = 'completed' and payment_id is not null
    group by payment_id
)
update payment p
set status = case when c.refunded >= p.amount then 'refunded' else 'partially_refunded' end
from completed_by_payment c
where p.payment_id = c.payment_id;

create index if not exists idx_refund_work_queue
    on refund (status, requested_at, refund_id)
    where status in ('requested','approved','processing','failed');

create index if not exists idx_issue_work_queue
    on issue (status, created_at, issue_id)
    where status in ('open','in_progress');
>>>>>>> 327a19993fe956540087376c122302c65ddffcde
