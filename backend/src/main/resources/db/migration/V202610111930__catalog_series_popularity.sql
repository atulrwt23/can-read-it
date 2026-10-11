-- Views in the last 7 days, copied in by discovery's view roll-up through the catalog's public API, so browse can
-- sort by popularity without reading another module's tables. Expand-only: older releases ignore the column.
alter table catalog.series add column popularity bigint not null default 0 check (popularity >= 0);

create index series_popular_idx
    on catalog.series (popularity desc, id desc)
    where visibility = 'PUBLISHED';
