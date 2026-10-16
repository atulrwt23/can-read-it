-- Notifications module: new-chapter emails for followed series (docs/modules/notifications.md). user_id refers to
-- identity.users, series_id and chapter_id to catalog, without foreign keys (one schema per module, ADR 0003).
create schema if not exists notifications;

-- A reader's email choices. Created on first use; new-chapter emails are on by default.
create table notifications.preferences (
    user_id            uuid        not null primary key,
    new_chapter_emails boolean     not null default true,
    -- Random; lets the link in each email turn new-chapter emails off without signing in.
    unsubscribe_token  text        not null unique,
    last_digest_at     timestamptz,
    updated_at         timestamptz not null
);

-- Chapters released since a follower's last digest, waiting to be emailed.
create table notifications.pending_chapters (
    user_id      uuid        not null,
    chapter_id   uuid        not null,
    series_id    uuid        not null,
    number       text        not null,
    published_at timestamptz not null,
    queued_at    timestamptz not null,
    primary key (user_id, chapter_id)
);

create index pending_chapters_queued_idx on notifications.pending_chapters (queued_at);
