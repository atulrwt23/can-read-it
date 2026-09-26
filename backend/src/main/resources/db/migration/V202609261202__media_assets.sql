create table media.assets (
    id           uuid        not null primary key default uuidv7(),
    kind         text        not null check (kind in ('COVER', 'PAGE', 'AVATAR')),
    visibility   text        not null default 'PUBLIC' check (visibility in ('PUBLIC', 'PROTECTED')),
    -- Immutable, content-addressed object key, so CDNs may cache public objects forever.
    storage_key  text        not null unique,
    sha256       text        not null check (sha256 ~ '^[0-9a-f]{64}$'),
    content_type text        not null,
    width        int         not null check (width > 0),
    height       int         not null check (height > 0),
    bytes        bigint      not null check (bytes > 0),
    status       text        not null default 'UPLOADED'
                             check (status in ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    variants     jsonb       not null default '{}'::jsonb,
    created_at   timestamptz not null default now()
);

create index assets_sha256_idx on media.assets (sha256);
