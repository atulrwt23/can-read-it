package com.canreadit.analytics.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the daily counts current and removes raw events past their retention. */
@Component
class AnalyticsJobs {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsJobs.class);

    private final AnalyticsRepository repository;
    private final AnalyticsProperties properties;
    private final Clock clock;

    AnalyticsJobs(AnalyticsRepository repository, AnalyticsProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Hourly: recounts yesterday (late events) and today. */
    @Scheduled(cron = "${app.analytics.rollup-cron:0 7 * * * *}", zone = "UTC")
    void rollUp() {
        repository.rollUp(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(1));
    }

    /** Daily: counts once more, then deletes raw events older than the retention window. */
    @Scheduled(cron = "${app.analytics.cleanup-cron:0 37 4 * * *}", zone = "UTC")
    void cleanUp() {
        rollUp();
        int deleted = repository.deleteBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Deleted {} analytics event(s) past retention", deleted);
        }
    }
}
