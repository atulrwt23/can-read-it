package com.canreadit.catalog.internal;

import com.canreadit.catalog.SeriesPopularity;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PopularityService implements SeriesPopularity {

    private final JdbcClient jdbc;

    PopularityService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void update(Map<UUID, Long> sevenDayViews) {
        sevenDayViews.forEach((id, views) ->
                jdbc.sql("""
                        update catalog.series set popularity = :views
                        where id = :id and popularity <> :views
                        """).param("id", id).param("views", Math.max(0, views)).update());
    }
}
