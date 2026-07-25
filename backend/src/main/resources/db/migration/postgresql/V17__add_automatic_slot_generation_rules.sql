insert into system_setting (setting_key, setting_value, setting_group, description, status, created_at, updated_at)
select 'slot.opening_time', '06:00', 'slot_generation',
       'Daily opening time used to generate bookable slots (HH:mm)', 'active', current_timestamp, current_timestamp
where not exists (select 1 from system_setting where setting_key = 'slot.opening_time');

insert into system_setting (setting_key, setting_value, setting_group, description, status, created_at, updated_at)
select 'slot.closing_time', '22:00', 'slot_generation',
       'Daily closing time used to generate bookable slots (HH:mm)', 'active', current_timestamp, current_timestamp
where not exists (select 1 from system_setting where setting_key = 'slot.closing_time');

insert into system_setting (setting_key, setting_value, setting_group, description, status, created_at, updated_at)
select 'slot.duration_minutes', '120', 'slot_generation',
       'Length of each automatically generated slot in minutes', 'active', current_timestamp, current_timestamp
where not exists (select 1 from system_setting where setting_key = 'slot.duration_minutes');

insert into system_setting (setting_key, setting_value, setting_group, description, status, created_at, updated_at)
select 'slot.generation_horizon_days', '30', 'slot_generation',
       'Number of days in advance for automatic slot generation', 'active', current_timestamp, current_timestamp
where not exists (select 1 from system_setting where setting_key = 'slot.generation_horizon_days');
