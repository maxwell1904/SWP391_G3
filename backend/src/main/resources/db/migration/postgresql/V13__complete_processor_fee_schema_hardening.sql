do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where connamespace = current_schema()::regnamespace
          and conname = 'chk_payment_provider_amounts'
    ) then
        alter table payment add constraint chk_payment_provider_amounts check (
            (provider_fee_amount is null or provider_fee_amount >= 0)
            and (provider_net_amount is null or provider_net_amount >= 0)
        );
    end if;
end
$$;

-- Cash payments never have a processor fee. PayPal values remain null until
-- the exact processor breakdown is available.
update payment
set provider_fee_amount = 0,
    provider_net_amount = case
        when status in ('paid', 'partially_refunded', 'refunded') then amount
        else 0
    end
where payment_method = 'cash'
  and (provider_fee_amount is null or provider_net_amount is null);

create index if not exists idx_issue_work_queue
    on issue (status, created_at, issue_id)
    where status in ('open', 'in_progress');
