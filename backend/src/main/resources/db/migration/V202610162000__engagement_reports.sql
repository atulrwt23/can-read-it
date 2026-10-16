-- Reports on comments, and moderators removing comments (docs/modules/engagement.md). Expand only: the previous
-- release ignores the new columns and table.

-- A removed comment stays (for the audit trail and appeals) but is never shown.
alter table engagement.comments
    add column removed_at timestamptz,
    add column removed_by uuid;

create table engagement.reports (
    id          uuid        not null primary key,
    comment_id  uuid        not null,
    reporter_id uuid        not null,
    reason      text        not null check (reason in ('SPAM', 'HARASSMENT', 'SPOILER', 'OFF_TOPIC', 'OTHER')),
    note        text        check (length(note) between 1 and 500),
    created_at  timestamptz not null,
    -- Set when a moderator removes the comment or keeps it.
    resolved_at timestamptz,
    resolution  text        check (resolution in ('REMOVED', 'DISMISSED')),
    resolved_by uuid,
    check ((resolved_at is null) = (resolution is null))
);

-- One open report per reader and comment; the queue reads open reports by comment.
create unique index reports_open_once_idx on engagement.reports (comment_id, reporter_id) where resolved_at is null;
create index reports_open_idx on engagement.reports (created_at) where resolved_at is null;
create index reports_reporter_idx on engagement.reports (reporter_id);
