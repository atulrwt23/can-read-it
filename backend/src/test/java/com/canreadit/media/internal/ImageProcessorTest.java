package com.canreadit.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.canreadit.shared.ApiException;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    @Test
    void encodesWebpThatDecodesToTheSameSize() {
        BufferedImage image = ImageProcessor.decode(TestImages.encode("png", 40, 60, 0x00D1B2));

        var rendition = ImageProcessor.encodeWebp(image);

        assertThat(rendition.width()).isEqualTo(40);
        assertThat(rendition.height()).isEqualTo(60);
        var info = ImageInspector.inspect(rendition.bytes());
        assertThat(info.contentType()).isEqualTo("image/webp");
        BufferedImage decoded = ImageProcessor.decode(rendition.bytes());
        assertThat(decoded.getWidth()).isEqualTo(40);
        assertThat(decoded.getHeight()).isEqualTo(60);
        // Lossy, but a flat colour comes back close.
        int rgb = decoded.getRGB(20, 30);
        assertThat((rgb >> 8) & 0xFF).isBetween(0xC8, 0xDA);
    }

    @Test
    void scalesDownKeepingTheAspectRatio() {
        BufferedImage image = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB);

        BufferedImage fitted = ImageProcessor.fitWidth(image, 1080);

        assertThat(fitted.getWidth()).isEqualTo(1080);
        assertThat(fitted.getHeight()).isEqualTo(360);
    }

    @Test
    void neverScalesUp() {
        BufferedImage image = new BufferedImage(500, 700, BufferedImage.TYPE_INT_RGB);

        assertThat(ImageProcessor.fitWidth(image, 1080)).isSameAs(image);
    }

    @Test
    void handlesIndexedAndGreyImages() {
        BufferedImage grey = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage indexed = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_INDEXED);

        assertThat(ImageProcessor.encodeWebp(grey).bytes()).isNotEmpty();
        assertThat(ImageProcessor.encodeWebp(indexed).bytes()).isNotEmpty();
    }

    @Test
    void rejectsImagesTallerThanWebpAllows() {
        BufferedImage tall = new BufferedImage(1, ImageProcessor.WEBP_MAX_DIMENSION + 1, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> ImageProcessor.encodeWebp(tall))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("image_too_tall"));
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        byte[] png = TestImages.encode("png", 10, 10, 0);
        byte[] truncated = java.util.Arrays.copyOf(png, 20);

        assertThatThrownBy(() -> ImageProcessor.decode(truncated))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo("unsupported_image"));
    }
}
