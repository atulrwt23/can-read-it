package com.canreadit.reading.internal;

import com.canreadit.reading.ReadingQueries;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JdbcReadingQueries implements ReadingQueries {

    private final ReadingRepository repository;

    JdbcReadingQueries(ReadingRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<UUID> followers(UUID seriesId) {
        return repository.followers(seriesId);
    }
}
