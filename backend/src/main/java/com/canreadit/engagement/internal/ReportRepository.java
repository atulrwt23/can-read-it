package com.canreadit.engagement.internal;

import com.canreadit.engagement.CommentModeration.ReportedComment;
import com.canreadit.engagement.ReportReason;
import com.canreadit.shared.Ids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code engagement.reports} table, and removing reported comments. */
@Repository
class ReportRepository {

    private final JdbcClient jdbc;

    ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Files a report; a reader's second open report on the same comment changes nothing. */
    void report(UUID commentId, UUID reporterId, ReportReason reason, @Nullable String note, Instant now) {
        jdbc.sql("""
                        insert into engagement.reports (id, comment_id, reporter_id, reason, note, created_at)
                        values (:id, :comment, :reporter, :reason, :note, :now)
                        on conflict (comment_id, reporter_id) where resolved_at is null do nothing
                        """)
                .param("id", Ids.newId())
                .param("comment", commentId)
                .param("reporter", reporterId)
                .param("reason", reason.name())
                .param("note", note)
                .param("now", utc(now))
                .update();
    }

    long reportedCount() {
        return jdbc.sql("""
                        select count(distinct r.comment_id) from engagement.reports r
                        join engagement.comments c on c.id = r.comment_id and c.removed_at is null
                        where r.resolved_at is null
                        """).query(Long.class).single();
    }

    List<ReportedComment> reported(int offset, int limit) {
        record Row(
                UUID id,
                UUID userId,
                UUID seriesId,
                UUID chapterId,
                String body,
                Instant createdAt,
                int reports,
                Instant firstReportedAt) {}
        List<Row> rows = jdbc.sql("""
                        select c.id, c.user_id, c.series_id, c.chapter_id, c.body, c.created_at,
                               count(*) as reports, min(r.created_at) as first_reported_at
                        from engagement.reports r
                        join engagement.comments c on c.id = r.comment_id and c.removed_at is null
                        where r.resolved_at is null
                        group by c.id
                        order by min(r.created_at), c.id
                        offset :offset limit :limit
                        """)
                .param("offset", offset)
                .param("limit", limit)
                .query((rs, n) -> new Row(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("series_id", UUID.class),
                        rs.getObject("chapter_id", UUID.class),
                        rs.getString("body"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("reports"),
                        rs.getObject("first_reported_at", OffsetDateTime.class).toInstant()))
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<ReportReason, Integer>> reasons = new HashMap<>();
        Map<UUID, List<String>> notes = new HashMap<>();
        jdbc.sql("""
                        select comment_id, reason, note from engagement.reports
                        where resolved_at is null and comment_id in (:ids)
                        order by created_at
                        """).param("ids", rows.stream().map(Row::id).toList()).query(rs -> {
            UUID id = rs.getObject("comment_id", UUID.class);
            reasons.computeIfAbsent(id, k -> new EnumMap<>(ReportReason.class))
                    .merge(ReportReason.valueOf(rs.getString("reason")), 1, Integer::sum);
            String note = rs.getString("note");
            if (note != null) {
                notes.computeIfAbsent(id, k -> new ArrayList<>()).add(note);
            }
        });
        return rows.stream()
                .map(row -> new ReportedComment(
                        row.id(),
                        row.userId(),
                        row.seriesId(),
                        row.chapterId(),
                        row.body(),
                        row.createdAt(),
                        row.reports(),
                        Map.copyOf(reasons.getOrDefault(row.id(), Map.of())),
                        List.copyOf(notes.getOrDefault(row.id(), List.of())),
                        row.firstReportedAt()))
                .toList();
    }

    /** Marks a visible comment removed; false if there is none. */
    boolean removeComment(UUID commentId, UUID moderatorId, Instant now) {
        return jdbc.sql("""
                        update engagement.comments set removed_at = :now, removed_by = :moderator
                        where id = :id and removed_at is null
                        """)
                        .param("id", commentId)
                        .param("moderator", moderatorId)
                        .param("now", utc(now))
                        .update()
                > 0;
    }

    /** Closes the comment's open reports with this outcome; returns how many. */
    int resolve(UUID commentId, String resolution, UUID moderatorId, Instant now) {
        return jdbc.sql("""
                        update engagement.reports
                        set resolved_at = :now, resolution = :resolution, resolved_by = :moderator
                        where comment_id = :id and resolved_at is null
                        """)
                .param("id", commentId)
                .param("resolution", resolution)
                .param("moderator", moderatorId)
                .param("now", utc(now))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
