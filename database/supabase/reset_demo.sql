\set ON_ERROR_STOP on

begin;
set local lock_timeout = '10s';
set local statement_timeout = '120s';

create temp table demo_context as
select :'demo_date'::date as demo_date;

truncate table
    notification,
    refund,
    invoice,
    payment,
    booking_promotion,
    issue,
    booking_service,
    booking,
    slot,
    promotion,
    extra_service,
    field_price,
    field,
    field_type,
    customer_membership,
    membership_level,
    system_setting,
    app_user,
    role
restart identity cascade;

insert into role (role_name, description, status, created_at, updated_at) values
    ('Customer', 'Registered customer who books football fields', 'active', now(), now()),
    ('Staff', 'Venue operator who handles walk-in bookings and match-day operations', 'active', now(), now()),
    ('Admin', 'System administrator who manages the venue and reporting', 'active', now(), now());

insert into app_user (
    role_id, full_name, email, phone, password_hash, date_of_birth, address,
    status, lock_reason, auth_version, email_verified, created_at, updated_at
)
select r.role_id, seed.full_name, seed.email, seed.phone,
       crypt('GoalZone@123', gen_salt('bf', 10)), seed.date_of_birth, seed.address,
       seed.status, seed.lock_reason, 0, true, now(), now()
from (values
    ('Admin', 'Admin Manager', 'admin@goalzone.local', '0900000001', date '1995-04-12', 'GoalZone Main Office', 'active', null),
    ('Staff', 'Staff Operator', 'staff@goalzone.local', '0900000002', date '1998-09-21', 'GoalZone Front Desk', 'active', null),
    ('Customer', 'Nguyen Van Customer', 'customer@goalzone.local', '0900000003', date '2001-03-18', 'District 7, Ho Chi Minh City', 'active', null),
    ('Customer', 'Le Thi Member', 'member@goalzone.local', '0900000004', date '2000-11-06', 'Thu Duc City, Ho Chi Minh City', 'active', null),
    ('Customer', 'Tran Minh Gold', 'gold@goalzone.local', '0900000005', date '1999-07-24', 'Binh Thanh District, Ho Chi Minh City', 'active', null),
    ('Customer', 'Vo Hoang Long', 'locked@goalzone.local', '0900000006', date '2002-02-14', 'Ho Chi Minh City', 'locked', 'Repeated unsuccessful sign-in attempts')
) as seed(role_name, full_name, email, phone, date_of_birth, address, status, lock_reason)
join role r on r.role_name = seed.role_name;

insert into membership_level (
    level_name, required_completed_bookings, discount_percent, benefit_description,
    display_order, qualification_period, required_consecutive_periods, status, created_at, updated_at
) values
    ('Bronze', 0, 0.00, 'Standard online booking benefits', 1, 'lifetime', 1, 'active', now(), now()),
    ('Silver', 4, 5.00, '5% discount on eligible online bookings after 4 completed bookings', 2, 'lifetime', 1, 'active', now(), now()),
    ('Gold', 8, 10.00, '10% discount on eligible online bookings after 8 completed bookings', 3, 'lifetime', 1, 'active', now(), now()),
    ('Platinum', 12, 15.00, '15% discount and highest-priority member benefits after 12 completed bookings', 4, 'lifetime', 1, 'active', now(), now());

insert into customer_membership (
    customer_id, membership_level_id, completed_booking_count, progress_note,
    effective_from, created_at, updated_at
)
select u.user_id, ml.membership_level_id, seed.completed_count,
       seed.progress_note, dc.demo_date - 90, now(), now()
from (values
    ('customer@goalzone.local', 'Bronze', 1, '1 completed booking; 3 more required for Silver'),
    ('member@goalzone.local', 'Silver', 4, 'Silver qualified from 4 completed bookings'),
    ('gold@goalzone.local', 'Gold', 8, 'Gold qualified from 8 completed bookings'),
    ('locked@goalzone.local', 'Bronze', 0, 'No completed bookings')
) as seed(email, level_name, completed_count, progress_note)
join app_user u on u.email = seed.email
join membership_level ml on ml.level_name = seed.level_name
cross join demo_context dc;

insert into field_type (type_name, player_capacity, description, status, created_at, updated_at) values
    ('5-a-side', 10, 'Compact pitch for fast small-sided matches', 'active', now(), now()),
    ('7-a-side', 14, 'Balanced pitch size for the most common local format', 'active', now(), now()),
    ('11-a-side', 22, 'Full-size competition football field', 'active', now(), now());

insert into field (
    field_type_id, field_name, description, image_url, location, surface_type,
    status, created_at, updated_at
)
select ft.field_type_id, seed.field_name, seed.description, seed.image_url,
       seed.location, seed.surface_type, seed.status, now(), now()
from (values
    ('5-a-side', 'Field 5A', 'Well-lit artificial-turf field close to reception and changing rooms.',
     'https://images.unsplash.com/photo-1759210720456-c9814f721479?auto=format&fit=crop&w=1600&q=85',
     'Zone A · Main entrance', 'FIFA-quality artificial turf', 'active'),
    ('7-a-side', 'Field 7A', 'Spacious hybrid-grass field suited to evening leagues and company matches.',
     'https://images.unsplash.com/photo-1712168539418-0aa66404ee0d?auto=format&fit=crop&w=1600&q=85',
     'Zone B · Riverside', 'Hybrid grass', 'active'),
    ('11-a-side', 'Main Field 11A', 'Full-size natural-grass field with covered benches and spectator seating.',
     'https://images.unsplash.com/photo-1758227231013-8cff978f1dae?auto=format&fit=crop&w=1600&q=85',
     'Zone C · Grandstand', 'Natural grass', 'active'),
    ('5-a-side', 'Training Field', 'Secondary training field retained as an inactive record for the administration flow.',
     'https://images.unsplash.com/photo-1690892738385-515d0c045ec0?auto=format&fit=crop&w=1600&q=85',
     'Zone D · Training block', 'Artificial turf', 'inactive')
) as seed(type_name, field_name, description, image_url, location, surface_type, status)
join field_type ft on ft.type_name = seed.type_name;

insert into field_price (
    field_id, day_type, start_time, end_time, price, effective_from,
    status, created_at, updated_at
)
select f.field_id, seed.day_type, seed.start_time::time, seed.end_time::time,
       seed.price, dc.demo_date - 90, 'active', now(), now()
from (values
    ('Field 5A', 'weekday', '06:00', '17:00', 16.00::numeric),
    ('Field 5A', 'weekday', '17:00', '22:00', 22.00::numeric),
    ('Field 5A', 'weekend', '06:00', '22:00', 24.00::numeric),
    ('Field 7A', 'weekday', '06:00', '17:00', 20.00::numeric),
    ('Field 7A', 'weekday', '17:00', '22:00', 28.00::numeric),
    ('Field 7A', 'weekend', '06:00', '22:00', 30.00::numeric),
    ('Main Field 11A', 'weekday', '06:00', '17:00', 38.00::numeric),
    ('Main Field 11A', 'weekday', '17:00', '22:00', 48.00::numeric),
    ('Main Field 11A', 'weekend', '06:00', '22:00', 54.00::numeric),
    ('Training Field', 'all', '06:00', '22:00', 14.00::numeric)
) as seed(field_name, day_type, start_time, end_time, price)
join field f on f.field_name = seed.field_name
cross join demo_context dc;

insert into extra_service (
    service_name, service_type, description, unit_name, unit_price,
    stock_quantity, max_quantity_per_booking, status, created_at, updated_at
) values
    ('Match ball rental', 'rental', 'Size-5 match ball prepared before kick-off', 'ball', 3.00, 30, 2, 'active', now(), now()),
    ('Training bib set', 'rental', 'Two-colour set for up to 14 players', 'set', 4.00, 12, 2, 'active', now(), now()),
    ('Drinking water box', 'sale', '24 sealed 500 ml water bottles', 'box', 6.00, 50, 5, 'active', now(), now()),
    ('Qualified referee', 'staff_service', 'Venue-assigned referee for one match', 'match', 15.00, 4, 1, 'active', now(), now()),
    ('Goalkeeper glove rental', 'rental', 'One cleaned pair of goalkeeper gloves', 'pair', 4.50, 10, 2, 'active', now(), now());

insert into promotion (
    promotion_code, promotion_name, banner_url, description, discount_type,
    discount_value, max_discount_amount, min_booking_amount, usage_limit, used_count,
    start_date, end_date, applicable_field_type_id, applicable_membership_level_id,
    applicable_extra_service_id, applicable_day_type, applicable_start_time,
    applicable_end_time, stackable, status, created_at, updated_at
)
select seed.code, seed.name, seed.banner_url, seed.description, seed.discount_type,
       seed.discount_value, seed.max_discount, seed.min_amount, seed.usage_limit, seed.used_count,
       dc.demo_date - 30, dc.demo_date + 60, ft.field_type_id, ml.membership_level_id,
       es.extra_service_id, seed.day_type, seed.start_time::time, seed.end_time::time,
       seed.stackable, 'active', now(), now()
from (values
    ('WELCOME10', 'Welcome 10%', null, '10% off an eligible first checkout, capped at USD 5.00',
     'percent', 10.00::numeric, 5.00::numeric, 10.00::numeric, 100, 1, null, null, null, null, null, null, false),
    ('WEEKEND15', 'Weekend League 15%', null, '15% off weekend bookings, capped at USD 8.00',
     'percent', 15.00::numeric, 8.00::numeric, 20.00::numeric, 100, 0, null, null, null, 'weekend', '06:00', '22:00', false),
    ('SILVER5', 'Silver Member Bonus', null, 'USD 5.00 off for Silver members; membership discount may also apply',
     'fixed_amount', 5.00::numeric, null, 20.00::numeric, 50, 0, null, 'Silver', null, null, null, null, true),
    ('WATER2', 'Hydration Bundle', null, 'USD 2.00 off when a drinking-water box is included',
     'fixed_amount', 2.00::numeric, null, 20.00::numeric, 100, 0, null, null, 'Drinking water box', null, null, null, false)
) as seed(
    code, name, banner_url, description, discount_type, discount_value,
    max_discount, min_amount, usage_limit, used_count, field_type_name,
    membership_name, service_name, day_type, start_time, end_time, stackable
)
cross join demo_context dc
left join field_type ft on ft.type_name = seed.field_type_name
left join membership_level ml on ml.level_name = seed.membership_name
left join extra_service es on es.service_name = seed.service_name;

insert into system_setting (
    setting_key, setting_value, setting_group, description, status,
    updated_by, created_at, updated_at
)
select seed.setting_key, seed.setting_value, seed.setting_group, seed.description,
       'active', admin.user_id, now(), now()
from (values
    ('deposit.default_percent', '30', 'deposit', 'Default online-booking deposit percentage'),
    ('payment.pending_timeout_minutes', '15', 'payment', 'Minutes before an unpaid online booking hold expires'),
    ('refund.before_24h_percent', '100', 'refund', 'Refund percentage when cancellation is at least 24 hours before start'),
    ('refund.same_day_percent', '80', 'refund', 'Refund percentage for same-day cancellation before check-in'),
    ('notification.booking_reminder_hours', '24', 'notification', 'Hours before start when a booking reminder is sent'),
    ('slot.opening_time', '06:00', 'slot_generation', 'Daily opening time used for automatic slot generation'),
    ('slot.closing_time', '22:00', 'slot_generation', 'Daily closing time used for automatic slot generation'),
    ('slot.duration_minutes', '120', 'slot_generation', 'Length of each automatically generated slot in minutes'),
    ('slot.generation_horizon_days', '30', 'slot_generation', 'Number of days generated ahead of the current date')
) as seed(setting_key, setting_value, setting_group, description)
cross join (select user_id from app_user where email = 'admin@goalzone.local') admin;

insert into slot (
    field_id, slot_date, start_time, end_time, status, created_by, created_at, updated_at
)
select f.field_id, calendar.slot_date,
       (time '06:00' + n * interval '2 hours')::time,
       (time '08:00' + n * interval '2 hours')::time,
       'available', admin.user_id, now(), now()
from field f
cross join demo_context dc
cross join lateral generate_series(dc.demo_date - 70, dc.demo_date + 30, interval '1 day') as calendar(slot_date)
cross join generate_series(0, 7) as n
cross join (select user_id from app_user where email = 'admin@goalzone.local') admin
where f.status = 'active';

update slot s
set status = 'blocked',
    block_reason = seed.reason,
    block_note = seed.note,
    updated_at = now()
from (values
    ('Field 5A', '18:00'::time, 'Field maintenance', 'Routine turf inspection after the evening league'),
    ('Field 7A', '20:00'::time, 'Private event', 'Reserved for a pre-approved community event')
) as seed(field_name, start_time, reason, note)
join field f on f.field_name = seed.field_name
cross join demo_context dc
where s.field_id = f.field_id
  and s.slot_date = dc.demo_date
  and s.start_time = seed.start_time;

create temp table seed_booking (
    booking_code varchar(30),
    customer_email varchar(120),
    staff_email varchar(120),
    field_name varchar(100),
    day_offset integer,
    start_time time,
    status varchar(30),
    source varchar(30),
    field_amount numeric(12,2),
    service_amount numeric(12,2),
    promotion_discount numeric(12,2),
    membership_discount numeric(12,2),
    total_amount numeric(12,2),
    deposit_amount numeric(12,2),
    paid_amount numeric(12,2),
    remaining_amount numeric(12,2),
    cancellation_fee numeric(12,2),
    refundable_amount numeric(12,2),
    note varchar(255)
);

-- Completed bookings establish genuine membership history and populate reports.
insert into seed_booking
select 'BK-CUST-' || to_char(n, 'FM00'), 'customer@goalzone.local', 'staff@goalzone.local',
       'Field 5A', -14, '10:00', 'completed', 'walk_in',
       16.00, 0.00, 1.60, 0.00, 14.40, 4.32, 14.40, 0.00, 0.00, 0.00,
       'Completed walk-in booking with WELCOME10'
from generate_series(1, 1) n;

insert into seed_booking
select 'BK-SILVER-' || to_char(n, 'FM00'), 'member@goalzone.local', 'staff@goalzone.local',
       'Field 7A', -(n * 7), '08:00', 'completed', 'walk_in',
       20.00, 0.00, 0.00, 0.00, 20.00, 6.00, 20.00, 0.00, 0.00, 0.00,
       'Completed booking contributing to Silver membership'
from generate_series(1, 4) n;

insert into seed_booking
select 'BK-GOLD-' || to_char(n, 'FM00'), 'gold@goalzone.local', 'staff@goalzone.local',
       'Main Field 11A', -(n * 7), '08:00', 'completed', 'walk_in',
       38.00, 0.00, 0.00, 0.00, 38.00, 11.40, 38.00, 0.00, 0.00, 0.00,
       'Completed booking contributing to Gold membership'
from generate_series(1, 8) n;

-- Operational records for the live Staff and Customer walkthrough.
insert into seed_booking values
    ('BK-GZ-2701', 'customer@goalzone.local', 'staff@goalzone.local', 'Field 5A', 0, '08:00',
     'confirmed', 'walk_in', 16.00, 3.00, 0.00, 0.00, 19.00, 5.70, 19.00, 0.00, 0.00, 19.00,
     'Morning five-a-side match; match ball prepared'),
    ('BK-GZ-2702', 'member@goalzone.local', 'staff@goalzone.local', 'Field 7A', 0, '10:00',
     'confirmed', 'walk_in', 20.00, 6.00, 0.00, 0.00, 26.00, 7.80, 7.80, 18.20, 0.00, 7.80,
     'Deposit collected at the counter; remaining balance due at check-in'),
    ('BK-GZ-2703', 'gold@goalzone.local', 'staff@goalzone.local', 'Main Field 11A', 0, '14:00',
     'confirmed', 'walk_in', 38.00, 15.00, 0.00, 0.00, 53.00, 15.90, 53.00, 0.00, 0.00, 53.00,
     'Full-size match with venue referee'),
    ('BK-RF-2901', 'customer@goalzone.local', 'staff@goalzone.local', 'Field 5A', 2, '12:00',
     'cancelled', 'walk_in', 16.00, 0.00, 0.00, 0.00, 16.00, 4.80, 16.00, 0.00, 0.00, 16.00,
     'Cancelled more than 24 hours before start; refund awaiting staff review'),
    ('BK-RF-2501', 'member@goalzone.local', 'staff@goalzone.local', 'Field 7A', -2, '14:00',
     'cancelled', 'walk_in', 20.00, 0.00, 0.00, 0.00, 20.00, 6.00, 20.00, 0.00, 0.00, 0.00,
     'Historical cash refund completed correctly');

insert into booking (
    customer_id, staff_id, slot_id, booking_code, status, booking_source,
    field_price_amount, service_total_amount, promotion_discount_amount,
    membership_discount_amount, total_amount, deposit_amount, paid_amount,
    remaining_amount, cancellation_fee_amount, refundable_amount, note,
    confirmed_at, completed_at, cancelled_at, created_at, updated_at
)
select customer.user_id, staff.user_id, s.slot_id, sb.booking_code, sb.status, sb.source,
       sb.field_amount, sb.service_amount, sb.promotion_discount, sb.membership_discount,
       sb.total_amount, sb.deposit_amount, sb.paid_amount, sb.remaining_amount,
       sb.cancellation_fee, sb.refundable_amount, sb.note,
       case when sb.status in ('confirmed', 'completed', 'cancelled') then dc.demo_date + sb.day_offset + time '07:00' end,
       case when sb.status = 'completed' then dc.demo_date + sb.day_offset + sb.start_time + interval '2 hours' end,
       case when sb.status = 'cancelled' then dc.demo_date - interval '1 day' + time '15:00' end,
       dc.demo_date + sb.day_offset - interval '2 days' + time '09:00',
       now()
from seed_booking sb
join app_user customer on customer.email = sb.customer_email
left join app_user staff on staff.email = sb.staff_email
join field f on f.field_name = sb.field_name
join demo_context dc on true
join slot s on s.field_id = f.field_id
           and s.slot_date = dc.demo_date + sb.day_offset
           and s.start_time = sb.start_time;

insert into booking_service (
    booking_id, extra_service_id, quantity, unit_price, line_total, created_at, updated_at
)
select b.booking_id, es.extra_service_id, seed.quantity, es.unit_price,
       es.unit_price * seed.quantity, now(), now()
from (values
    ('BK-GZ-2701', 'Match ball rental', 1),
    ('BK-GZ-2702', 'Drinking water box', 1),
    ('BK-GZ-2703', 'Qualified referee', 1)
) as seed(booking_code, service_name, quantity)
join booking b on b.booking_code = seed.booking_code
join extra_service es on es.service_name = seed.service_name;

insert into booking_promotion (
    booking_id, promotion_id, promotion_code_snapshot, discount_amount, usage_counted, applied_at
)
select b.booking_id, p.promotion_id, p.promotion_code, 1.60, true, b.created_at
from booking b
join promotion p on p.promotion_code = 'WELCOME10'
where b.booking_code = 'BK-CUST-01';

insert into payment (
    booking_id, payment_code, payment_option, payment_method, amount, status,
    transaction_code, provider_status, currency, provider_fee_amount,
    provider_net_amount, gateway_message, paid_at, created_by, created_at, updated_at
)
select b.booking_id, 'PAY-' || b.booking_code,
       case when b.booking_code = 'BK-GZ-2702' then 'deposit' else 'full' end,
       'cash',
       case when b.booking_code = 'BK-GZ-2702' then 7.80 else b.paid_amount end,
       case when b.booking_code = 'BK-RF-2501' then 'refunded' else 'paid' end,
       'CASH-' || replace(b.booking_code, 'BK-', ''),
       case when b.booking_code = 'BK-RF-2501' then 'MANUAL_CASH_REFUND' else 'CASH_RECORDED' end,
       'USD', 0.00,
       case when b.booking_code = 'BK-GZ-2702' then 7.80 else b.paid_amount end,
       case when b.booking_code = 'BK-RF-2501'
            then 'Cash payment returned by venue staff'
            else 'Cash payment recorded at the venue' end,
       coalesce(b.confirmed_at, b.created_at), staff.user_id, b.created_at, now()
from booking b
cross join (select user_id from app_user where email = 'staff@goalzone.local') staff
where b.paid_amount > 0;

insert into invoice (
    booking_id, invoice_code, field_amount, service_amount, discount_amount,
    total_amount, paid_amount, remaining_amount, refund_amount,
    issued_at, created_at, updated_at
)
select b.booking_id, 'INV-' || b.booking_code, b.field_price_amount,
       b.service_total_amount, b.promotion_discount_amount + b.membership_discount_amount,
       b.total_amount, b.paid_amount, b.remaining_amount,
       case when b.booking_code = 'BK-RF-2501' then 20.00 else 0.00 end,
       coalesce(b.confirmed_at, b.created_at), b.created_at, now()
from booking b
where b.paid_amount > 0;

insert into refund (
    booking_id, payment_id, refund_code, requested_by, processed_by,
    refund_amount, refund_reason, status, transaction_code, provider_status,
    idempotency_key, gateway_message, requested_at, processed_at, created_at, updated_at
)
select b.booking_id, p.payment_id, seed.refund_code, customer.user_id,
       case when seed.status = 'completed' then staff.user_id end,
       seed.amount, seed.reason, seed.status, seed.transaction_code, seed.provider_status,
       seed.idempotency_key, seed.gateway_message,
       dc.demo_date - interval '1 day' + time '15:05',
       case when seed.status = 'completed' then dc.demo_date - interval '1 day' + time '15:15' end,
       dc.demo_date - interval '1 day' + time '15:05', now()
from (values
    ('BK-RF-2901', 'RF-GZ-2901', 16.00::numeric,
     'Schedule changed; customer requested the eligible refund', 'requested', null, null,
     'GZ-REFUND-2901', 'Awaiting staff review'),
    ('BK-RF-2501', 'RF-GZ-2501', 20.00::numeric,
     'Match cancelled before start', 'completed', 'CASH-REFUND-COMPLETE', 'MANUAL_CASH_REFUND',
     'GZ-REFUND-2501', 'Cash refund recorded by venue staff')
) as seed(
    booking_code, refund_code, amount, reason, status, transaction_code,
    provider_status, idempotency_key, gateway_message
)
join booking b on b.booking_code = seed.booking_code
join payment p on p.booking_id = b.booking_id
join app_user customer on customer.user_id = b.customer_id
cross join (select user_id from app_user where email = 'staff@goalzone.local') staff
cross join demo_context dc;

insert into issue (
    reporter_id, booking_id, field_id, extra_service_id, assigned_staff_id,
    title, description, status, resolution_note, resolved_at, created_at, updated_at
)
select reporter.user_id, b.booking_id, f.field_id, es.extra_service_id, staff.user_id,
       seed.title, seed.description, seed.status, seed.resolution_note,
       case when seed.status = 'resolved' then dc.demo_date - interval '3 days' + time '18:30' end,
       dc.demo_date + seed.day_offset + time '16:00', now()
from (values
    ('customer@goalzone.local', 'BK-GZ-2701', 'Field 5A', null,
     'Floodlight check requested', 'One floodlight was flickering during warm-up; please inspect before the next match.',
     'open', null, 0),
    ('member@goalzone.local', 'BK-SILVER-01', 'Field 7A', 'Training bib set',
     'Missing training bibs', 'The prepared set had two bibs missing.',
     'resolved', 'Staff supplied replacement bibs and corrected the inventory count.', -4)
) as seed(
    reporter_email, booking_code, field_name, service_name,
    title, description, status, resolution_note, day_offset
)
join app_user reporter on reporter.email = seed.reporter_email
join booking b on b.booking_code = seed.booking_code
join field f on f.field_name = seed.field_name
left join extra_service es on es.service_name = seed.service_name
cross join (select user_id from app_user where email = 'staff@goalzone.local') staff
cross join demo_context dc;

insert into notification (
    user_id, booking_id, payment_id, refund_id, notification_type,
    title, message, is_read, sent_at, created_at
)
select b.customer_id, b.booking_id, null::bigint, null::bigint, 'booking_confirmation',
       'Booking confirmed',
       'Booking ' || b.booking_code || ' is ready for the scheduled match.',
       b.status in ('completed', 'cancelled'), b.confirmed_at, coalesce(b.confirmed_at, b.created_at)
from booking b
where b.booking_code in ('BK-GZ-2701', 'BK-GZ-2702', 'BK-GZ-2703')
union all
select b.customer_id, b.booking_id, p.payment_id, null::bigint, 'payment',
       'Payment recorded',
       'USD ' || to_char(p.amount, 'FM999990.00') || ' was recorded for ' || b.booking_code || '.',
       false, p.paid_at, p.paid_at
from payment p
join booking b on b.booking_id = p.booking_id
where b.booking_code in ('BK-GZ-2701', 'BK-GZ-2702', 'BK-GZ-2703')
union all
select b.customer_id, b.booking_id, null::bigint, r.refund_id, 'refund',
       case when r.status = 'completed' then 'Refund completed' else 'Refund requested' end,
       'Refund ' || r.refund_code || ' is ' || r.status || '.',
       r.status = 'completed', r.requested_at, r.requested_at
from refund r
join booking b on b.booking_id = r.booking_id
union all
select i.reporter_id, i.booking_id, null::bigint, null::bigint, 'issue',
       case when i.status = 'resolved' then 'Issue resolved' else 'Issue received' end,
       case when i.status = 'resolved' then i.resolution_note else 'Venue staff will review your report.' end,
       i.status = 'resolved', coalesce(i.resolved_at, i.created_at), i.created_at
from issue i;

-- Keep sequence values correct even if this file is adapted later to use explicit IDs.
select setval(pg_get_serial_sequence('role', 'role_id'), coalesce(max(role_id), 1), true) from role;
select setval(pg_get_serial_sequence('app_user', 'user_id'), coalesce(max(user_id), 1), true) from app_user;
select setval(pg_get_serial_sequence('booking', 'booking_id'), coalesce(max(booking_id), 1), true) from booking;

commit;

\echo 'GoalZone demo database reset completed for demo date' :demo_date
