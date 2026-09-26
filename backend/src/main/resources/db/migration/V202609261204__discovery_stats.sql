-- Discovery read model (ADR 0005). series_id refers to catalog.series without a foreign key.

create table discovery.series_views_daily (
    series_id uuid   not null,
    day       date   not null,
    views     bigint not null default 0 check (views >= 0),
    primary key (series_id, day)
);

create table discovery.series_stats (
    series_id    uuid         not null primary key,
    views_today  bigint       not null default 0 check (views_today >= 0),
    views_7d     bigint       not null default 0 check (views_7d >= 0),
    views_all    bigint       not null default 0 check (views_all >= 0),
    follows      bigint       not null default 0 check (follows >= 0),
    rating_avg   numeric(3,2) check (rating_avg between 0 and 5),
    rating_count int          not null default 0 check (rating_count >= 0),
    updated_at   timestamptz  not null default now()
);

create index series_stats_today_idx on discovery.series_stats (views_today desc, series_id);
create index series_stats_7d_idx on discovery.series_stats (views_7d desc, series_id);
create index series_stats_all_idx on discovery.series_stats (views_all desc, series_id);
