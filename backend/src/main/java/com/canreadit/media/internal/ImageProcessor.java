package com.canreadit.media.internal;

import com.canreadit.shared.ApiException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Decodes, scales and re-encodes images to WebP. Decoding goes through ImageIO (PNG and JPEG built in, WebP from
 * the libwebp plugin); encoding is lossy WebP. Re-encoding keeps only pixels, so no metadata survives.
 */
final class ImageProcessor {

    /** WebP's own limit on either side. */
    static final int WEBP_MAX_DIMENSION = 16_383;

    static final float QUALITY = 0.82f;

    record Rendition(byte[] bytes, int width, int height) {}

    private ImageProcessor() {}

    static BufferedImage decode(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw unreadable();
            }
            return image;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw unreadable();
        }
    }

    /** Scales down to {@code maxWidth} keeping the aspect ratio; narrower images keep their size. */
    static BufferedImage fitWidth(BufferedImage source, int maxWidth) {
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        int width = maxWidth;
        int height = Math.max(1, Math.round((float) source.getHeight() * width / source.getWidth()));
        // Halve in steps first, so a large reduction stays sharp with bilinear filtering.
        BufferedImage current = source;
        while (current.getWidth() / 2 >= width) {
            current = draw(current, current.getWidth() / 2, Math.max(1, current.getHeight() / 2));
        }
        return draw(current, width, height);
    }

    static Rendition encodeWebp(BufferedImage image) {
        if (image.getWidth() > WEBP_MAX_DIMENSION || image.getHeight() > WEBP_MAX_DIMENSION) {
            throw ApiException.badRequest(
                    "image_too_tall",
                    "Images can be at most %,d px on each side after resizing. Split long pages into parts."
                            .formatted(WEBP_MAX_DIMENSION));
        }
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
        var out = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("Lossy");
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(normalize(image), null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode WebP", e);
        } finally {
            writer.dispose();
        }
        return new Rendition(out.toByteArray(), image.getWidth(), image.getHeight());
    }

    /** The encoder takes RGB or ARGB; indexed and grey images are drawn into one of those first. */
    private static BufferedImage normalize(BufferedImage image) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        return draw(image, image.getWidth(), image.getHeight());
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        var target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static ApiException unreadable() {
        return ApiException.badRequest("unsupported_image", "The image could not be read. Use PNG, JPEG or WebP.");
    }
}
