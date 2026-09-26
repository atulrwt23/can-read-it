package com.canreadit.catalog.internal;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Flat-colour PNG placeholders for local seed data. Never used for real content. */
final class PlaceholderImages {

    private PlaceholderImages() {}

    static byte[] cover(String title, String typeLabel, int rgb) {
        int width = 600;
        int height = 900;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        Color base = new Color(rgb);
        g.setColor(base);
        g.fillRect(0, 0, width, height);
        g.setColor(shade(base, 0.82f));
        g.fillOval(-120, 80, 520, 520);
        g.setColor(shade(base, 1.15f));
        g.fillOval(260, 260, 420, 420);
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, height - 300, width, 300);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        int y = height - 230;
        for (String line : wrap(title, g.getFontMetrics(), width - 80)) {
            g.drawString(line, 40, y);
            y += 58;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        g.drawString(typeLabel + " · placeholder cover", 40, height - 40);
        g.dispose();
        return png(image);
    }

    static byte[] page(String seriesTitle, String chapter, int pageNumber, int height, int rgb) {
        int width = 720;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        Color base = new Color(rgb);
        g.setColor(shade(base, 1.35f));
        g.fillRect(0, 0, width, height);

        // A few "panels" so vertical scrolling looks like a comic strip.
        g.setColor(shade(base, 1.1f));
        int top = 40;
        int panelHeight = (height - 80 - 40) / 3;
        for (int i = 0; i < 3; i++) {
            g.fillRoundRect(40, top, width - 80, panelHeight, 12, 12);
            top += panelHeight + 20;
        }
        g.setColor(shade(base, 0.45f));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        drawCentered(g, "Chapter " + chapter, width, height / 2 - 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        drawCentered(g, "Page " + pageNumber, width, height / 2 + 20);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        drawCentered(g, seriesTitle, width, height / 2 + 60);
        g.dispose();
        return png(image);
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    private static void drawCentered(Graphics2D g, String text, int width, int baseline) {
        g.drawString(text, (width - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        return lines.subList(0, Math.min(lines.size(), 3));
    }

    private static Color shade(Color color, float factor) {
        return new Color(
                Math.min(255, Math.round(color.getRed() * factor)),
                Math.min(255, Math.round(color.getGreen() * factor)),
                Math.min(255, Math.round(color.getBlue() * factor)));
    }

    private static byte[] png(BufferedImage image) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
