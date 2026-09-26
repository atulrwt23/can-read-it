package com.canreadit.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CursorsTest {

    @Test
    void roundTripsParts() {
        String cursor = Cursors.encode("title", "Ünïcode, commas & spaces", "");

        assertThat(Cursors.decode(cursor, 3)).containsExactly("title", "Ünïcode, commas & spaces", "");
    }

    @Test
    void isUrlSafe() {
        assertThat(Cursors.encode("??>>", "~~~")).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void rejectsWrongPartCount() {
        String cursor = Cursors.encode("a", "b");

        assertThatThrownBy(() -> Cursors.decode(cursor, 3))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("invalid_cursor"));
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Cursors.decode("not base64!!", 1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Cursors.decode("x".repeat(2000), 1)).isInstanceOf(ApiException.class);
    }
}
