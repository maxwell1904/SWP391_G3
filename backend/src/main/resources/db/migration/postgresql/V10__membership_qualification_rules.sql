-- Membership rules in the original AnPTT backlog include lifetime, monthly,
-- and consecutive-week thresholds. Existing tiers keep lifetime behaviour.
alter table membership_level
    add column if not exists qualification_period varchar(20) not null default 'lifetime',
    add column if not exists required_consecutive_periods integer not null default 1;

alter table membership_level
    add constraint chk_membership_qualification_period
    check (qualification_period in ('lifetime', 'monthly', 'weekly'));

alter table membership_level
    add constraint chk_membership_consecutive_periods
    check (required_consecutive_periods >= 1);
