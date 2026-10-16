-- Engagement module: series ratings (docs/modules/engagement.md). user_id refers to identity.users and series_id to
-- catalog.series, without foreign keys (one schema per module, ADR 0003).
create schema if not exists engagement;

-- One score per reader and series; rating again replaces it.
create table engagement.ratings (
    user_id    uuid        not null,
    series_id  uuid        not null,
    score      smallint    not null check (score between 1 and 5),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (user_id, series_id)
);

-- Running totals per series, changed in the same transaction as the ratings, so a series page reads one row.
create table engagement.series_ratings (
    series_id  uuid        not null primary key,
    ratings    integer     not null check (ratings >= 0),
    score_sum  bigint      not null check (score_sum >= 0),
    updated_at timestamptz not null
);
