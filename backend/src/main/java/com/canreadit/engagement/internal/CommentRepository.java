package com.canreadit.engagement.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code engagement.comments} table. */
@Repository
class CommentRepository {

    private final JdbcClient jdbc;

    CommentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Comment(UUID id, UUID seriesId, UUID chapterId, UUID userId, String body, Instant createdAt) {}

    void insert(Comment comment) {
        jdbc.sql("""
                        insert into engagement.comments (id, series_id, chapter_id, user_id, body, created_at)
                        values (:id, :series, :chapter, :user, :body, :at)
                        """)
                .param("id", comment.id())
                .param("series", comment.seriesId())
                .param("chapter", comment.chapterId())
                .param("user", comment.userId())
                .param("body", comment.body())
                .param("at", utc(comment.createdAt()))
                .update();
    }

    /** A chapter's comments, newest first, starting after {@code before} (a comment ID) when given. */
    List<Comment> page(UUID chapterId, @Nullable UUID before, int limit) {
        return jdbc.sql("""
                        select id, series_id, chapter_id, user_id, body, created_at from engagement.comments
                        where chapter_id = :chapter and removed_at is null
                          and (cast(:before as uuid) is null or id < :before)
                        order by id desc
                        limit :limit
                        """)
                .param("chapter", chapterId)
                .param("before", before)
                .param("limit", limit)
                .query((rs, n) -> new Comment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("series_id", UUID.class),
                        rs.getObject("chapter_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("body"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    /** Deletes the comment, and any reports on it, if this reader wrote it. */
    boolean deleteOwn(UUID commentId, UUID userId) {
        jdbc.sql("""
                        delete from engagement.reports
                        where comment_id = (select id from engagement.comments where id = :id and user_id = :user)
                        """).param("id", commentId).param("user", userId).update();
        return jdbc.sql("delete from engagement.comments where id = :id and user_id = :user")
                        .param("id", commentId)
                        .param("user", userId)
                        .update()
                > 0;
    }

    /** Erases a reader's comments, the reports on them, and the reports they made. */
    void deleteAllBy(UUID userId) {
        jdbc.sql("""
                        delete from engagement.reports
                        where reporter_id = :user
                           or comment_id in (select id from engagement.comments where user_id = :user)
                        """).param("user", userId).update();
        jdbc.sql("delete from engagement.comments where user_id = :user")
                .param("user", userId)
                .update();
    }

    /** The author of a comment readers can still see. */
    Optional<UUID> visibleAuthor(UUID commentId) {
        return jdbc.sql("select user_id from engagement.comments where id = :id and removed_at is null")
                .param("id", commentId)
                .query(UUID.class)
                .optional();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
