alter table refund add column if not exists provider_status varchar(50);
alter table refund add column if not exists idempotency_key varchar(120);
alter table refund add column if not exists gateway_message varchar(255);

create unique index if not exists uk_refund_idempotency_key
    on refund (idempotency_key)
    where idempotency_key is not null;

create unique index if not exists uk_refund_transaction_code
    on refund (transaction_code)
    where transaction_code is not null;
