package com.canreadit.moderation.internal;

import com.canreadit.shared.Ids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The audit log: one row per moderator decision, never changed afterwards. */
@Repository
class ActionRepository {

    enum Action {
        REMOVE_COMMENT,
        KEEP_COMMENT
    }

    record LoggedAction(
            UUID id,
            UUID moderatorId,
            Action action,
            UUID targetId,
            @Nullable String note,
            Instant at) {}

    private final JdbcClient jdbc;

    ActionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void log(UUID moderatorId, Action action, UUID commentId, @Nullable String note, Instant at) {
        jdbc.sql("""
                        insert into moderation.actions (id, moderator_id, action, target_type, target_id, note, created_at)
                        values (:id, :moderator, :action, 'COMMENT', :target, :note, :at)
                        """)
                .param("id", Ids.newId())
                .param("moderator", moderatorId)
                .param("action", action.name())
                .param("target", commentId)
                .param("note", note)
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .update();
    }

    List<LoggedAction> recent(int limit) {
        return jdbc.sql("""
                        select id, moderator_id, action, target_id, note, created_at from moderation.actions
                        order by created_at desc, id desc
                        limit :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new LoggedAction(
                        rs.getObject("id", UUID.class),
                        rs.getObject("moderator_id", UUID.class),
                        Action.valueOf(rs.getString("action")),
                        rs.getObject("target_id", UUID.class),
                        rs.getString("note"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
