-- Chapter comments (docs/modules/engagement.md). user_id refers to identity.users, series_id and chapter_id to
-- catalog, without foreign keys (one schema per module, ADR 0003).
create table engagement.comments (
    -- UUIDv7, so ordering by id is ordering by time.
    id         uuid        not null primary key,
    series_id  uuid        not null,
    chapter_id uuid        not null,
    user_id    uuid        not null,
    -- Plain text; the web app escapes it like any other text.
    body       text        not null check (length(body) between 1 and 2000),
    created_at timestamptz not null
);

-- Newest first per chapter (keyset pagination), and everything one reader wrote (account erasure).
create index comments_chapter_idx on engagement.comments (chapter_id, id desc);
create index comments_user_idx on engagement.comments (user_id);
