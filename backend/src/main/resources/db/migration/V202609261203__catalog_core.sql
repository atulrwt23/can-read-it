-- Catalog: Series -> Edition (one per language) -> Chapter.
-- Cross-module references (cover_asset_id, asset_id, user_id) are plain uuids by design.

create table catalog.series (
    id                  uuid        not null primary key default uuidv7(),
    slug                text        not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title               text        not null check (length(title) between 1 and 300),
    alt_titles          text[]      not null default '{}',
    synopsis            text        not null default '',
    type                text        not null check (type in ('MANHWA', 'NOVEL')),
    status              text        not null check (status in ('ONGOING', 'COMPLETED', 'HIATUS')),
    visibility          text        not null default 'DRAFT'
                                    check (visibility in ('DRAFT', 'PUBLISHED', 'UNLISTED', 'REMOVED')),
    age_rating          text        not null default 'ALL' check (age_rating in ('ALL', 'TEEN', 'MATURE')),
    cover_asset_id      uuid,
    first_released_year smallint    check (first_released_year between 1900 and 2200),
    release_cadence     text,
    -- Denormalized from chapters by the publish job; drives "latest updates".
    last_published_at   timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

-- array_to_string is only STABLE, so wrap it to index titles + alt titles together.
create function catalog.search_text(title text, alt_titles text[]) returns text
    language sql immutable parallel safe
    return title || ' ' || array_to_string(alt_titles, ' ');

create index series_search_trgm_idx
    on catalog.series using gin (catalog.search_text(title, alt_titles) gin_trgm_ops);
create index series_synopsis_fts_idx
    on catalog.series using gin (to_tsvector('simple', synopsis));
create index series_updated_idx
    on catalog.series (coalesce(last_published_at, created_at) desc, id desc)
    where visibility = 'PUBLISHED';
create index series_newest_idx
    on catalog.series (created_at desc, id desc)
    where visibility = 'PUBLISHED';
create index series_title_idx
    on catalog.series (title, id)
    where visibility = 'PUBLISHED';

create table catalog.series_slug_history (
    old_slug   text        not null primary key,
    series_id  uuid        not null references catalog.series (id) on delete cascade,
    changed_at timestamptz not null default now()
);

create table catalog.genres (
    id   uuid not null primary key default uuidv7(),
    slug text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name text not null unique
);

create table catalog.series_genres (
    series_id uuid not null references catalog.series (id) on delete cascade,
    genre_id  uuid not null references catalog.genres (id) on delete cascade,
    primary key (series_id, genre_id)
);

create index series_genres_genre_idx on catalog.series_genres (genre_id, series_id);

create table catalog.editions (
    id                uuid    not null primary key default uuidv7(),
    series_id         uuid    not null references catalog.series (id) on delete cascade,
    language          text    not null check (language ~ '^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),
    is_original       boolean not null default false,
    translator_credit text,
    unique (series_id, language)
);

create unique index editions_one_original_idx on catalog.editions (series_id) where is_original;

create table catalog.chapters (
    id           uuid         not null primary key default uuidv7(),
    edition_id   uuid         not null references catalog.editions (id) on delete cascade,
    number       numeric(8,2) not null check (number >= 0),
    title        text,
    status       text         not null default 'DRAFT' check (status in ('DRAFT', 'SCHEDULED', 'PUBLISHED')),
    publish_at   timestamptz,
    published_at timestamptz,
    access       text         not null default 'FREE' check (access in ('FREE', 'EARLY_ACCESS')),
    price_coins  int          not null default 0 check (price_coins >= 0),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    unique (edition_id, number),
    check (status = 'DRAFT' or publish_at is not null)
);

-- Visibility rule (ADR 0006): status <> 'DRAFT' and publish_at <= now().
create index chapters_visible_idx on catalog.chapters (edition_id, number) where status <> 'DRAFT';

create table catalog.chapter_pages (
    chapter_id uuid not null references catalog.chapters (id) on delete cascade,
    page_index int  not null check (page_index >= 0),
    asset_id   uuid not null,
    width      int  not null check (width > 0),
    height     int  not null check (height > 0),
    primary key (chapter_id, page_index)
);

create table catalog.novel_chapter_bodies (
    chapter_id    uuid not null primary key references catalog.chapters (id) on delete cascade,
    body_markdown text not null,
    word_count    int  not null check (word_count >= 0)
);

create table catalog.series_members (
    series_id  uuid        not null references catalog.series (id) on delete cascade,
    user_id    uuid        not null,
    role       text        not null check (role in ('OWNER', 'EDITOR', 'UPLOADER')),
    created_at timestamptz not null default now(),
    primary key (series_id, user_id)
);

create index series_members_user_idx on catalog.series_members (user_id);
