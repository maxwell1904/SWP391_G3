alter table payment
    add column if not exists provider_fee_amount numeric(12, 2),
    add column if not exists provider_net_amount numeric(12, 2);

create index if not exists idx_refund_work_queue
    on refund (status, requested_at, refund_id)
    where status in ('requested', 'approved', 'processing', 'failed');
