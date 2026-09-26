package com.canreadit.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.canreadit.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ImageInspectorTest {

    @Test
    void readsPng() {
        var info = ImageInspector.inspect(TestImages.encode("png", 30, 45, 0x00D1B2));

        assertThat(info).isEqualTo(new ImageInspector.ImageInfo("image/png", "png", 30, 45));
    }

    @Test
    void readsJpeg() {
        var info = ImageInspector.inspect(TestImages.encode("jpg", 64, 20, 0x485FC7));

        assertThat(info).isEqualTo(new ImageInspector.ImageInfo("image/jpeg", "jpg", 64, 20));
    }

    @Test
    void readsExtendedWebp() {
        byte[] b = webpHeader("VP8X");
        // canvas width-1 = 799 and height-1 = 1199, 24-bit little endian
        b[24] = (byte) 0x1F;
        b[25] = 0x03;
        b[26] = 0;
        b[27] = (byte) 0xAF;
        b[28] = 0x04;
        b[29] = 0;

        assertThat(ImageInspector.inspect(b)).isEqualTo(new ImageInspector.ImageInfo("image/webp", "webp", 800, 1200));
    }

    @Test
    void readsLosslessWebp() {
        byte[] b = webpHeader("VP8L");
        b[20] = 0x2F;
        long bits = (99L) | (49L << 14); // width 100, height 50
        for (int i = 0; i < 4; i++) {
            b[21 + i] = (byte) (bits >> (8 * i));
        }

        assertThat(ImageInspector.inspect(b)).isEqualTo(new ImageInspector.ImageInfo("image/webp", "webp", 100, 50));
    }

    @Test
    void rejectsSvgGifAndGarbage() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] gif = TestImages.encode("gif", 10, 10, 0);
        byte[] truncatedPng = Arrays.copyOf(TestImages.encode("png", 10, 10, 0), 12);

        for (byte[] content : new byte[][] {svg, gif, truncatedPng, new byte[0]}) {
            assertThatThrownBy(() -> ImageInspector.inspect(content))
                    .isInstanceOfSatisfying(
                            ApiException.class, e -> assertThat(e.code()).isEqualTo("unsupported_image"));
        }
    }

    private static byte[] webpHeader(String chunk) {
        byte[] b = new byte[40];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy(chunk.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        return b;
    }
}
