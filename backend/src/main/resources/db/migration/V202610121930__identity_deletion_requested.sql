-- When a reader asked to delete their account. The account is erased 30 days later unless they sign in again.
alter table identity.users add column deletion_requested_at timestamptz;

create index users_pending_deletion_idx on identity.users (deletion_requested_at)
    where status = 'PENDING_DELETION';
