alter table public.barns
    add column if not exists placement_epoch_day integer not null default 2147483647,
    add column if not exists placement_age_weeks integer not null default 18,
    add column if not exists comfort_temp_min double precision not null default 18.0,
    add column if not exists comfort_temp_max double precision not null default 28.0,
    add column if not exists sensor_deviation_max double precision not null default 2.0;

notify pgrst, 'reload schema';
