package com.canreadit.notifications.internal;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The {@code notifications} schema: readers' email choices and the chapters waiting for their next digest. */
@Repository
class NotificationRepository {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;

    NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Preferences(UUID userId, boolean newChapterEmails, String unsubscribeToken) {}

    record Pending(UUID userId, UUID chapterId, UUID seriesId, String number, Instant publishedAt) {}

    // --- Preferences

    /** The reader's choices, created with the defaults on first use. */
    Preferences preferences(UUID userId, Instant now) {
        jdbc.sql("""
                        insert into notifications.preferences (user_id, unsubscribe_token, updated_at)
                        values (:user, :token, :now)
                        on conflict (user_id) do nothing
                        """)
                .param("user", userId)
                .param("token", newToken())
                .param("now", utc(now))
                .update();
        return jdbc.sql("""
                        select user_id, new_chapter_emails, unsubscribe_token from notifications.preferences
                        where user_id = :user
                        """)
                .param("user", userId)
                .query((rs, n) -> new Preferences(
                        rs.getObject("user_id", UUID.class),
                        rs.getBoolean("new_chapter_emails"),
                        rs.getString("unsubscribe_token")))
                .single();
    }

    /** Whether new-chapter emails are on; on until the reader turns them off. */
    boolean newChapterEmails(UUID userId) {
        return jdbc.sql("select new_chapter_emails from notifications.preferences where user_id = :user")
                .param("user", userId)
                .query(Boolean.class)
                .optional()
                .orElse(true);
    }

    void setNewChapterEmails(UUID userId, boolean enabled, Instant now) {
        preferences(userId, now);
        jdbc.sql("""
                        update notifications.preferences set new_chapter_emails = :enabled, updated_at = :now
                        where user_id = :user
                        """)
                .param("user", userId)
                .param("enabled", enabled)
                .param("now", utc(now))
                .update();
        if (!enabled) {
            deletePending(userId);
        }
    }

    /** Turns new-chapter emails off for the token's owner; empty if no reader has this token. */
    Optional<UUID> unsubscribe(String token, Instant now) {
        Optional<UUID> userId = jdbc.sql("""
                        update notifications.preferences set new_chapter_emails = false, updated_at = :now
                        where unsubscribe_token = :token
                        returning user_id
                        """)
                .param("token", token)
                .param("now", utc(now))
                .query(UUID.class)
                .optional();
        userId.ifPresent(this::deletePending);
        return userId;
    }

    /** Readers among these who turned new-chapter emails off. */
    Set<UUID> optedOut(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                        select user_id from notifications.preferences
                        where user_id in (:users) and not new_chapter_emails
                        """)
                .param("users", List.copyOf(userIds))
                .query(UUID.class)
                .list());
    }

    // --- Pending chapters

    void queue(
            Collection<UUID> userIds, UUID chapterId, UUID seriesId, String number, Instant publishedAt, Instant now) {
        for (UUID userId : userIds) {
            jdbc.sql("""
                            insert into notifications.pending_chapters
                                (user_id, chapter_id, series_id, number, published_at, queued_at)
                            values (:user, :chapter, :series, :number, :published, :now)
                            on conflict do nothing
                            """)
                    .param("user", userId)
                    .param("chapter", chapterId)
                    .param("series", seriesId)
                    .param("number", number)
                    .param("published", utc(publishedAt))
                    .param("now", utc(now))
                    .update();
        }
    }

    /**
     * Readers with chapters waiting whose last digest was before {@code lastDigestBefore} (or who never had one),
     * oldest waiting first.
     */
    List<UUID> dueReaders(Instant lastDigestBefore, int limit) {
        return jdbc.sql("""
                        select p.user_id from notifications.pending_chapters p
                        left join notifications.preferences pr on pr.user_id = p.user_id
                        where pr.user_id is null
                           or (pr.new_chapter_emails and (pr.last_digest_at is null or pr.last_digest_at < :before))
                        group by p.user_id
                        order by min(p.queued_at)
                        limit :limit
                        """)
                .param("before", utc(lastDigestBefore))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    List<Pending> pending(UUID userId) {
        return jdbc.sql("""
                        select user_id, chapter_id, series_id, number, published_at
                        from notifications.pending_chapters
                        where user_id = :user
                        order by series_id, published_at, number
                        """)
                .param("user", userId)
                .query((rs, n) -> new Pending(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("chapter_id", UUID.class),
                        rs.getObject("series_id", UUID.class),
                        rs.getString("number"),
                        rs.getObject("published_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    /** After a digest went out: forgets what it listed and remembers when. */
    void digestSent(UUID userId, Collection<UUID> chapterIds, Instant now) {
        if (!chapterIds.isEmpty()) {
            jdbc.sql("delete from notifications.pending_chapters where user_id = :user and chapter_id in (:chapters)")
                    .param("user", userId)
                    .param("chapters", List.copyOf(chapterIds))
                    .update();
        }
        jdbc.sql("update notifications.preferences set last_digest_at = :now where user_id = :user")
                .param("user", userId)
                .param("now", utc(now))
                .update();
    }

    void deletePending(UUID userId) {
        jdbc.sql("delete from notifications.pending_chapters where user_id = :user")
                .param("user", userId)
                .update();
    }

    void deletePending(UUID userId, UUID seriesId) {
        jdbc.sql("delete from notifications.pending_chapters where user_id = :user and series_id = :series")
                .param("user", userId)
                .param("series", seriesId)
                .update();
    }

    /** Drops chapters that waited too long to be worth an email. */
    int deletePendingBefore(Instant queuedBefore) {
        return jdbc.sql("delete from notifications.pending_chapters where queued_at < :before")
                .param("before", utc(queuedBefore))
                .update();
    }

    /** Erases everything kept about the user (the account was deleted). */
    void forget(UUID userId) {
        deletePending(userId);
        jdbc.sql("delete from notifications.preferences where user_id = :user")
                .param("user", userId)
                .update();
    }

    /**
     * Locks the reader's row until the transaction ends if a digest is due: emails on, and none sent since
     * {@code lastDigestBefore}. False if it isn't due, or another instance is sending it right now.
     */
    boolean claimDigest(UUID userId, Instant lastDigestBefore) {
        return jdbc.sql("""
                        select user_id from notifications.preferences
                        where user_id = :user and new_chapter_emails
                          and (last_digest_at is null or last_digest_at < :before)
                        for update skip locked
                        """)
                .param("user", userId)
                .param("before", utc(lastDigestBefore))
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
