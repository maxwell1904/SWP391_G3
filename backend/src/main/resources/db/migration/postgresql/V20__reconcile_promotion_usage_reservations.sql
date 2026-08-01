alter table booking_promotion
    add column if not exists usage_counted boolean not null default false;

update booking_promotion bp
set usage_counted = true
from booking b
where b.booking_id = bp.booking_id
  and bp.discount_amount > 0
  and b.status in ('pending', 'confirmed', 'checked_in', 'completed', 'no_show');

alter table promotion
    drop constraint if exists chk_promotion_used_count;

alter table promotion
    add constraint chk_promotion_used_count check (used_count >= 0);

update promotion p
set used_count = usage.actual_count
from (
    select p2.promotion_id, count(bp.booking_promotion_id)::integer as actual_count
    from promotion p2
    left join booking_promotion bp
        on bp.promotion_id = p2.promotion_id
       and bp.usage_counted = true
    group by p2.promotion_id
) usage
where usage.promotion_id = p.promotion_id;

comment on column booking_promotion.usage_counted is
    'True while this applied promotion reserves or consumes campaign capacity.';
