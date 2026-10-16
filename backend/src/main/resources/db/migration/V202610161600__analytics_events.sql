-- Analytics module: anonymous product events and their daily counts (docs/modules/analytics.md). Nothing here
-- identifies a person: visitor is a fingerprint keyed with a secret that changes daily, series_id refers to
-- catalog.series without a foreign key.
create schema if not exists analytics;

-- Raw events, kept for a limited time (app.analytics.retention).
create table analytics.events (
    id          uuid        not null primary key,
    occurred_at timestamptz not null,
    name        text        not null check (name ~ '^[A-Z_]{1,40}$'),
    visitor     text,
    path        text,
    series_id   uuid,
    chapter     text,
    value       integer
);

create index events_occurred_idx on analytics.events (occurred_at);
create index events_series_idx on analytics.events (name, occurred_at, series_id) where series_id is not null;

-- Per day and event: how many events and distinct visitors. Kept after the raw events are gone.
create table analytics.daily_counts (
    day      date   not null,
    name     text   not null,
    events   bigint not null check (events >= 0),
    visitors bigint not null check (visitors >= 0),
    primary key (day, name)
);
