-- Spring Modulith event publication registry (transactional outbox), schema v2.
create table modulith.event_publication (
    id                     uuid        not null primary key,
    listener_id            text        not null,
    event_type             text        not null,
    serialized_event       text        not null,
    publication_date       timestamptz not null,
    completion_date        timestamptz,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamptz
);

create index event_publication_serialized_event_hash_idx
    on modulith.event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx
    on modulith.event_publication (completion_date);
