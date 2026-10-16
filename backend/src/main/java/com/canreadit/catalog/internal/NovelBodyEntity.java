package com.canreadit.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** The text of a novel chapter ({@code catalog.novel_chapter_bodies}), in Markdown. */
@Entity
@Table(name = "novel_chapter_bodies", schema = "catalog")
class NovelBodyEntity implements Persistable<UUID> {

    @Id
    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "body_markdown", nullable = false)
    private String markdown;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Transient
    private boolean isNew;

    protected NovelBodyEntity() {}

    NovelBodyEntity(UUID chapterId, String markdown) {
        this.chapterId = chapterId;
        this.isNew = true;
        replace(markdown);
    }

    @Override
    public UUID getId() {
        return chapterId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }

    void replace(String newMarkdown) {
        markdown = newMarkdown;
        wordCount = countWords(newMarkdown);
    }

    String markdown() {
        return markdown;
    }

    int wordCount() {
        return wordCount;
    }

    static int countWords(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
