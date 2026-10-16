package com.canreadit.engagement.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommentServiceTest {

    @Test
    void keepsParagraphsButNotExtraBlankLinesOrControlCharacters() {
        assertThat(CommentService.normalize("  a\r\nb\r\n\r\n\r\n\rc\u0000​\t "))
                .isEqualTo("a\nb\n\nc​");
        assertThat(CommentService.normalize("\n\n  \n")).isEmpty();
    }
}
