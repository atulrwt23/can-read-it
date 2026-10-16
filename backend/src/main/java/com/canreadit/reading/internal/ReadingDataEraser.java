package com.canreadit.reading.internal;

import com.canreadit.identity.UserDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Deletes a reader's library, progress and read marks once their account is erased. */
@Component
class ReadingDataEraser {

    private final ReadingRepository repository;

    ReadingDataEraser(ReadingRepository repository) {
        this.repository = repository;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        repository.forget(event.userId());
    }
}
