package com.canreadit.engagement.internal;

import com.canreadit.identity.UserDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Removes a reader's ratings (and their weight in each series' average) and comments once their account is erased.
 */
@Component
class EngagementDataEraser {

    private final RatingService ratings;
    private final CommentService comments;

    EngagementDataEraser(RatingService ratings, CommentService comments) {
        this.ratings = ratings;
        this.comments = comments;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        ratings.forget(event.userId());
        comments.forget(event.userId());
    }
}
