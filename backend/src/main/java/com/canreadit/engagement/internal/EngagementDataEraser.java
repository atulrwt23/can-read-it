package com.canreadit.engagement.internal;

import com.canreadit.identity.UserDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Removes a reader's ratings, and their weight in each series' average, once their account is erased. */
@Component
class EngagementDataEraser {

    private final RatingService ratings;

    EngagementDataEraser(RatingService ratings) {
        this.ratings = ratings;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        ratings.forget(event.userId());
    }
}
