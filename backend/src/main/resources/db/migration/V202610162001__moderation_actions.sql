-- Moderation module: the audit log of what moderators did (docs/modules/moderation.md). moderator_id refers to
-- identity.users and target_id to the moderated item, without foreign keys (one schema per module, ADR 0003).
create schema if not exists moderation;

create table moderation.actions (
    id           uuid        not null primary key,
    moderator_id uuid        not null,
    action       text        not null check (action in ('REMOVE_COMMENT', 'KEEP_COMMENT')),
    target_type  text        not null check (target_type in ('COMMENT')),
    target_id    uuid        not null,
    note         text        check (length(note) between 1 and 500),
    created_at   timestamptz not null
);

create index actions_recent_idx on moderation.actions (created_at desc);
