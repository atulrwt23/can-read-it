package com.canreadit.catalog.internal;

import java.util.List;
import java.util.Random;

/** Generates filler prose for seeded novel chapters. */
final class NovelText {

    private static final List<String> OPENINGS = List.of(
            "The morning arrived without asking permission.",
            "Nobody in the room admitted to hearing the knock.",
            "It had been raining since before the letter came.",
            "The map was wrong again, and this time it was smug about it.",
            "She counted the steps twice and got two different answers.");
    private static final List<String> SENTENCES = List.of(
            "The streetlights flickered as if they were keeping a secret.",
            "He folded the note into smaller and smaller squares until it refused to fold again.",
            "Somewhere below, a bell rang once and then thought better of it.",
            "*Not yet*, she told herself, and for once she listened.",
            "The kettle sang a song nobody had taught it.",
            "Every door on the corridor was painted the same shade of almost-blue.",
            "They agreed on nothing except the direction of the wind.",
            "The city smelled of wet stone and **unfinished promises**.",
            "A cat watched the whole exchange with professional disinterest.",
            "The clock on the wall ran four minutes fast, out of habit.",
            "His answer was careful, the way a person steps onto ice.",
            "The pages of the ledger were warm, as though someone had just closed it.");

    private NovelText() {}

    static String chapter(String seriesTitle, String number, Random random) {
        var text = new StringBuilder();
        text.append(OPENINGS.get(random.nextInt(OPENINGS.size())));
        int paragraphs = 6 + random.nextInt(5);
        for (int p = 0; p < paragraphs; p++) {
            text.append(p == 0 ? " " : "\n\n");
            int sentences = 3 + random.nextInt(4);
            for (int s = 0; s < sentences; s++) {
                if (s > 0) {
                    text.append(' ');
                }
                text.append(SENTENCES.get(random.nextInt(SENTENCES.size())));
            }
        }
        text.append("\n\n*End of chapter ")
                .append(number)
                .append(" of ")
                .append(seriesTitle)
                .append(". This is placeholder text for local development.*");
        return text.toString();
    }

    static String chapterTitle(String number) {
        return number.contains(".") ? "Extra: A Quiet Interlude" : "Chapter " + number;
    }

    static int wordCount(String markdown) {
        String stripped = markdown.replaceAll("[*_#>]", " ").strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }
}
