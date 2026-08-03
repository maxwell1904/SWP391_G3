-- Expand the classroom catalogue without replacing existing operational data.
-- The reset script mirrors these records for clean demo resets.

insert into field_type (
    type_name, player_capacity, description, status, created_at, updated_at
)
select '11-a-side', 22, 'Full-size competition football field', 'active', now(), now()
where not exists (
    select 1 from field_type where type_name = '11-a-side'
);

insert into field (
    field_type_id, field_name, description, image_url, location, surface_type,
    status, created_at, updated_at
)
select ft.field_type_id, seed.field_name, seed.description, seed.image_url,
       seed.location, seed.surface_type, 'active', now(), now()
from (values
    ('5-a-side', 'Field 5B',
     'Compact artificial-turf field beside the parking and motorbike area.',
     'https://images.unsplash.com/photo-1579952363873-27f3bade9f55?auto=format&fit=crop&w=1600&q=85',
     'Zone A · Parking side', 'Artificial turf'),
    ('5-a-side', 'Covered Field 5C',
     'Covered five-a-side field for rainy-day and late-evening sessions.',
     'https://images.unsplash.com/photo-1690892738385-515d0c045ec0?auto=format&fit=crop&w=1600&q=85',
     'Zone B · Covered block', 'Artificial turf'),
    ('7-a-side', 'Field 7B',
     'Competition field with team benches and floodlights for local leagues.',
     'https://images.unsplash.com/photo-1553778263-73a83bab9b0c?auto=format&fit=crop&w=1600&q=85',
     'Zone C · League block', 'Artificial turf'),
    ('7-a-side', 'Field 7C',
     'Community field suited to company, school, and weekend matches.',
     'https://images.unsplash.com/photo-1526232761682-d26e03ac148e?auto=format&fit=crop&w=1600&q=85',
     'Zone C · Community block', 'Artificial turf'),
    ('11-a-side', 'Main Field 11B',
     'Second full-size field for tournaments and structured training sessions.',
     'https://images.unsplash.com/photo-1459865264687-595d652de67e?auto=format&fit=crop&w=1600&q=85',
     'Zone D · East stand', 'Hybrid grass')
) as seed(type_name, field_name, description, image_url, location, surface_type)
join field_type ft on ft.type_name = seed.type_name
where not exists (
    select 1
    from field existing
    where lower(existing.field_name) = lower(seed.field_name)
);

insert into field_price (
    field_id, day_type, start_time, end_time, price, effective_from,
    status, created_at, updated_at
)
select f.field_id, seed.day_type, seed.start_time::time, seed.end_time::time,
       seed.price, current_date, 'active', now(), now()
from (values
    ('Field 5B', 'weekday', '06:00', '17:00', 15.00::numeric),
    ('Field 5B', 'weekday', '17:00', '22:00', 21.00::numeric),
    ('Field 5B', 'weekend', '06:00', '22:00', 23.00::numeric),
    ('Covered Field 5C', 'all', '06:00', '22:00', 20.00::numeric),
    ('Field 7B', 'weekday', '06:00', '17:00', 22.00::numeric),
    ('Field 7B', 'weekday', '17:00', '22:00', 30.00::numeric),
    ('Field 7B', 'weekend', '06:00', '22:00', 32.00::numeric),
    ('Field 7C', 'weekday', '06:00', '17:00', 21.00::numeric),
    ('Field 7C', 'weekday', '17:00', '22:00', 29.00::numeric),
    ('Field 7C', 'weekend', '06:00', '22:00', 31.00::numeric),
    ('Main Field 11B', 'weekday', '06:00', '17:00', 35.00::numeric),
    ('Main Field 11B', 'weekday', '17:00', '22:00', 45.00::numeric),
    ('Main Field 11B', 'weekend', '06:00', '22:00', 50.00::numeric)
) as seed(field_name, day_type, start_time, end_time, price)
join field f on f.field_name = seed.field_name
where not exists (
    select 1
    from field_price existing
    where existing.field_id = f.field_id
      and existing.day_type = seed.day_type
      and existing.start_time = seed.start_time::time
      and existing.end_time = seed.end_time::time
      and existing.status = 'active'
);
