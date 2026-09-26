package com.canreadit.media.internal;

import com.canreadit.shared.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Identifies images by their magic bytes (never by file name or client-declared type) and reads
 * their dimensions from the header. Only PNG, JPEG and WebP are accepted; SVG never is.
 */
final class ImageInspector {

    static final int MAX_DIMENSION = 32_767;

    record ImageInfo(String contentType, String extension, int width, int height) {}

    private ImageInspector() {}

    static ImageInfo inspect(byte[] b) {
        ImageInfo info;
        if (isPng(b)) {
            info = new ImageInfo("image/png", "png", int32be(b, 16), int32be(b, 20));
        } else if (isJpeg(b)) {
            info = jpeg(b);
        } else if (isWebp(b)) {
            info = webp(b);
        } else {
            throw unsupported();
        }
        if (info.width() < 1 || info.height() < 1 || info.width() > MAX_DIMENSION || info.height() > MAX_DIMENSION) {
            throw unsupported();
        }
        return info;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 24
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G'
                && b[4] == 0x0D
                && b[5] == 0x0A
                && b[6] == 0x1A
                && b[7] == 0x0A
                && b[12] == 'I'
                && b[13] == 'H'
                && b[14] == 'D'
                && b[15] == 'R';
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 30
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P';
    }

    /** Walks JPEG segments until a start-of-frame marker, which carries the dimensions. */
    private static ImageInfo jpeg(byte[] b) {
        int i = 2;
        while (i + 9 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                throw unsupported();
            }
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) { // fill byte
                i++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // standalone markers
                i += 2;
                continue;
            }
            boolean startOfFrame =
                    marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (startOfFrame) {
                return new ImageInfo("image/jpeg", "jpg", uint16be(b, i + 7), uint16be(b, i + 5));
            }
            i += 2 + uint16be(b, i + 2);
        }
        throw unsupported();
    }

    private static ImageInfo webp(byte[] b) {
        String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> new ImageInfo("image/webp", "webp", uint24le(b, 24) + 1, uint24le(b, 27) + 1);
            case "VP8L" -> {
                if ((b[20] & 0xFF) != 0x2F) {
                    throw unsupported();
                }
                long bits = (b[21] & 0xFFL) | (b[22] & 0xFFL) << 8 | (b[23] & 0xFFL) << 16 | (b[24] & 0xFFL) << 24;
                yield new ImageInfo("image/webp", "webp", (int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
            }
            case "VP8 " -> {
                if ((b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) {
                    throw unsupported();
                }
                yield new ImageInfo("image/webp", "webp", uint16le(b, 26) & 0x3FFF, uint16le(b, 28) & 0x3FFF);
            }
            default -> throw unsupported();
        };
    }

    private static int int32be(byte[] b, int i) {
        return (b[i] & 0xFF) << 24 | (b[i + 1] & 0xFF) << 16 | (b[i + 2] & 0xFF) << 8 | (b[i + 3] & 0xFF);
    }

    private static int uint16be(byte[] b, int i) {
        return (b[i] & 0xFF) << 8 | (b[i + 1] & 0xFF);
    }

    private static int uint16le(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8;
    }

    private static int uint24le(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16;
    }

    private static ApiException unsupported() {
        return new ApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_image", "Only PNG, JPEG and WebP images are accepted.");
    }
}
