-- Reading module (CLAUDE.md section 6): a reader's library, progress and read chapters. user_id refers to
-- identity.users and series_id/chapter_id to catalog, without foreign keys (one schema per module, ADR 0003).
create schema if not exists reading;

-- Series a reader follows. Following puts a series in the library; notifications will use it (phase 2).
create table reading.library_entries (
    user_id   uuid        not null,
    series_id uuid        not null,
    added_at  timestamptz not null default now(),
    primary key (user_id, series_id)
);

create index library_entries_newest_idx on reading.library_entries (user_id, added_at desc, series_id desc);

-- Where a reader is in each series: one row per series, the latest update wins.
create table reading.reading_progress (
    user_id    uuid          not null,
    series_id  uuid          not null,
    chapter_id uuid          not null,
    position   numeric(5, 4) not null check (position between 0 and 1),
    updated_at timestamptz   not null,
    primary key (user_id, series_id)
);

create index reading_progress_recent_idx on reading.reading_progress (user_id, updated_at desc, series_id desc);

-- Chapters read to the end. series_id is kept so a series' marks load in one query.
create table reading.read_chapters (
    user_id    uuid        not null,
    chapter_id uuid        not null,
    series_id  uuid        not null,
    read_at    timestamptz not null,
    primary key (user_id, chapter_id)
);

create index read_chapters_series_idx on reading.read_chapters (user_id, series_id);
