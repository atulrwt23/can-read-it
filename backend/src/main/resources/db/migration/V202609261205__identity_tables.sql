-- Identity tables (CLAUDE.md section 7). Sign-in flows arrive in roadmap step 2.

create table identity.users (
    id                uuid        not null primary key default uuidv7(),
    -- Nullable for Apple private-relay edge cases.
    email             citext      unique,
    email_verified_at timestamptz,
    handle            citext      not null unique check (handle ~ '^[A-Za-z0-9_]{3,30}$'),
    display_name      text        not null check (length(display_name) between 1 and 60),
    avatar_asset_id   uuid,
    status            text        not null default 'ACTIVE'
                                  check (status in ('ACTIVE', 'SUSPENDED', 'BANNED', 'PENDING_DELETION', 'DELETED')),
    birth_year        smallint    check (birth_year between 1900 and 2200),
    locale            text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);

create table identity.credentials (
    user_id             uuid        not null primary key references identity.users (id) on delete cascade,
    password_hash       text        not null,
    password_updated_at timestamptz not null default now()
);

create table identity.user_identities (
    id               uuid        not null primary key default uuidv7(),
    user_id          uuid        not null references identity.users (id) on delete cascade,
    provider         text        not null check (provider in ('GOOGLE', 'APPLE')),
    provider_subject text        not null,
    provider_email   citext,
    linked_at        timestamptz not null default now(),
    unique (provider, provider_subject)
);

create index user_identities_user_idx on identity.user_identities (user_id);

create table identity.user_roles (
    user_id    uuid        not null references identity.users (id) on delete cascade,
    role       text        not null check (role in ('READER', 'CREATOR', 'MODERATOR', 'ADMIN')),
    granted_by uuid,
    granted_at timestamptz not null default now(),
    primary key (user_id, role)
);

create table identity.sessions (
    id           uuid        not null primary key default uuidv7(),
    user_id      uuid        not null references identity.users (id) on delete cascade,
    device_name  text,
    platform     text,
    ip           inet,
    user_agent   text,
    created_at   timestamptz not null default now(),
    last_used_at timestamptz not null default now(),
    expires_at   timestamptz not null,
    revoked_at   timestamptz
);

create index sessions_user_idx on identity.sessions (user_id);

create table identity.refresh_tokens (
    id         uuid        not null primary key default uuidv7(),
    session_id uuid        not null references identity.sessions (id) on delete cascade,
    token_hash text        not null unique,
    issued_at  timestamptz not null default now(),
    used_at    timestamptz,
    expires_at timestamptz not null
);

create index refresh_tokens_session_idx on identity.refresh_tokens (session_id);
create index refresh_tokens_expires_idx on identity.refresh_tokens (expires_at);

create table identity.verification_codes (
    id         uuid        not null primary key default uuidv7(),
    email      citext      not null,
    purpose    text        not null
                           check (purpose in ('SIGN_IN', 'VERIFY_EMAIL', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    code_hash  text        not null,
    attempts   int         not null default 0 check (attempts >= 0),
    expires_at timestamptz not null,
    used_at    timestamptz
);

create index verification_codes_lookup_idx on identity.verification_codes (email, purpose, expires_at desc);
create index verification_codes_expires_idx on identity.verification_codes (expires_at);

create table identity.auth_events (
    id         uuid        not null primary key default uuidv7(),
    user_id    uuid,
    type       text        not null,
    ip         inet,
    user_agent text,
    created_at timestamptz not null default now()
);

create index auth_events_user_idx on identity.auth_events (user_id, created_at desc);
create index auth_events_created_idx on identity.auth_events (created_at);
