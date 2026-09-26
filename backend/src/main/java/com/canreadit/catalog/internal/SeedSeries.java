package com.canreadit.catalog.internal;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Invented placeholder series for local development. None of these are real works. */
record SeedSeries(
        String slug,
        String title,
        List<String> altTitles,
        boolean novel,
        String status,
        String visibility,
        String ageRating,
        List<String> genres,
        int color,
        String cadenceLabel,
        Duration cadence,
        int year,
        int chapters,
        Duration lastUpdateAgo,
        String synopsis,
        int extraHalfChapterAfter,
        boolean scheduledNext,
        boolean earlyAccessLatest,
        @Nullable String oldSlug) {

    private static final Duration WEEKLY = Duration.ofDays(7);
    private static final Duration DAILY = Duration.ofDays(1);

    static List<SeedSeries> all() {
        return List.of(
                manhwa("tower-of-quiet-stars", "Tower of Quiet Stars", 0x2E6F9E, List.of("fantasy", "action"))
                        .alt("조용한 별의 탑")
                        .chapters(18, Duration.ofMinutes(40))
                        .halfAfter(12)
                        .scheduled()
                        .oldSlug("quiet-stars-tower")
                        .synopsis(
                                "A night-shift janitor finds a staircase that only appears when the city lights go out, "
                                        + "and every floor asks a question he is not ready to answer.")
                        .build(),
                manhwa("second-shift-hero", "Second Shift Hero", 0xC0504D, List.of("action", "comedy"))
                        .chapters(14, Duration.ofHours(3))
                        .earlyAccess()
                        .synopsis("By day she files insurance claims. By night she files them too, for the monsters "
                                + "she keeps accidentally defeating.")
                        .build(),
                manhwa("paper-moon-cafe", "Paper Moon Cafe", 0xD08C60, List.of("romance", "slice-of-life"))
                        .chapters(20, Duration.ofHours(9))
                        .synopsis("Two rival baristas share one tiny counter and a lease neither of them can afford.")
                        .build(),
                manhwa(
                                "blade-of-the-ninth-gate",
                                "Blade of the Ninth Gate",
                                0x4B3F72,
                                List.of("martial-arts", "action"))
                        .alt("Ninth Gate Blade")
                        .chapters(16, Duration.ofDays(1))
                        .scheduled()
                        .synopsis("The last disciple of a forgotten sect must pass eight gates guarded by her own "
                                + "former masters.")
                        .build(),
                manhwa("glass-garden-academy", "Glass Garden Academy", 0x5B9E6F, List.of("drama", "romance"))
                        .status("COMPLETED")
                        .chapters(12, Duration.ofDays(30))
                        .synopsis("At a boarding school built inside a greenhouse, every student is growing something "
                                + "they would rather keep hidden.")
                        .build(),
                manhwa("signal-from-deep-orbit", "Signal from Deep Orbit", 0x1F4E5F, List.of("sci-fi", "thriller"))
                        .chapters(10, Duration.ofDays(2))
                        .synopsis("A radio operator on a mining station receives a message in her own voice, "
                                + "sent three days from now.")
                        .build(),
                manhwa("the-dukes-borrowed-name", "The Duke's Borrowed Name", 0x8E5572, List.of("romance", "fantasy"))
                        .status("HIATUS")
                        .chapters(9, Duration.ofDays(60))
                        .synopsis("A forger signs one document too many and wakes up engaged to the man whose "
                                + "signature she copied.")
                        .build(),
                manhwa("ghost-ledger", "Ghost Ledger", 0x3C3C3C, List.of("mystery", "thriller"))
                        .age("TEEN")
                        .chapters(15, Duration.ofDays(4))
                        .synopsis("An accountant audits the books of a haunted company and finds the dead are "
                                + "still on the payroll.")
                        .build(),
                manhwa(
                                "cloud-kitchen-chronicles",
                                "Cloud Kitchen Chronicles",
                                0xE0A030,
                                List.of("comedy", "slice-of-life"))
                        .status("COMPLETED")
                        .chapters(8, Duration.ofDays(45))
                        .synopsis("Four roommates run a delivery-only restaurant out of an apartment with one "
                                + "working burner.")
                        .build(),
                manhwa("iron-petal", "Iron Petal", 0x6D7B8D, List.of("action", "sci-fi"))
                        .age("MATURE")
                        .chapters(11, Duration.ofDays(6))
                        .synopsis("A retired mech pilot is pulled back for one last mission, piloting a machine "
                                + "that remembers her better than she remembers it.")
                        .build(),
                novel("a-map-of-unwritten-cities", "A Map of Unwritten Cities", 0x3F6E8C, List.of("fantasy", "mystery"))
                        .chapters(22, Duration.ofHours(1))
                        .scheduled()
                        .synopsis("A cartographer inherits an atlas of cities that do not exist yet, and one of "
                                + "them is being built on her street.")
                        .build(),
                novel(
                                "the-clockmakers-apprentice",
                                "The Clockmaker's Apprentice",
                                0x7A5C3E,
                                List.of("fantasy", "drama"))
                        .status("COMPLETED")
                        .chapters(25, Duration.ofDays(20))
                        .synopsis("Every clock in the valley stops at once, except the one the apprentice was "
                                + "told never to wind.")
                        .build(),
                novel("salt-and-starlight", "Salt and Starlight", 0x2F8F9D, List.of("romance", "drama"))
                        .chapters(17, Duration.ofHours(6))
                        .synopsis("A lighthouse keeper and a stranded astronomer trade letters by tide.")
                        .build(),
                novel(
                                "rebooted-at-level-one",
                                "Rebooted at Level One",
                                0x7B61FF,
                                List.of("action", "fantasy", "comedy"))
                        .alt("Level One Reboot")
                        .chapters(24, Duration.ofDays(1))
                        .earlyAccess()
                        .synopsis("The world's top-ranked adventurer respawns with none of her gear and all of "
                                + "her enemies' grudges.")
                        .build(),
                novel("whispers-under-rainfall", "Whispers Under Rainfall", 0x4A5D6B, List.of("mystery", "thriller"))
                        .chapters(13, Duration.ofDays(3))
                        .synopsis("In a town where it has rained for a year, a detective follows the only "
                                + "footprints that stay dry.")
                        .build(),
                novel("letters-to-the-winter-prince", "Letters to the Winter Prince", 0x9FB4C7, List.of("romance"))
                        .status("HIATUS")
                        .visibility("UNLISTED")
                        .chapters(8, Duration.ofDays(90))
                        .synopsis("An unlisted placeholder: reachable by direct link, absent from lists.")
                        .build());
    }

    private static Builder manhwa(String slug, String title, int color, List<String> genres) {
        return new Builder(slug, title, false, color, genres, "Weekly", WEEKLY);
    }

    private static Builder novel(String slug, String title, int color, List<String> genres) {
        return new Builder(slug, title, true, color, genres, "Daily", DAILY);
    }

    private static final class Builder {
        private final String slug;
        private final String title;
        private final boolean novel;
        private final int color;
        private final List<String> genres;
        private final String cadenceLabel;
        private final Duration cadence;
        private List<String> alt = List.of();
        private String status = "ONGOING";
        private String visibility = "PUBLISHED";
        private String age = "ALL";
        private int chapters = 10;
        private Duration lastUpdateAgo = Duration.ofDays(1);
        private String synopsis = "";
        private int halfAfter = -1;
        private boolean scheduled;
        private boolean earlyAccess;
        private @Nullable String oldSlug;

        Builder(
                String slug,
                String title,
                boolean novel,
                int color,
                List<String> genres,
                String label,
                Duration cadence) {
            this.slug = slug;
            this.title = title;
            this.novel = novel;
            this.color = color;
            this.genres = genres;
            this.cadenceLabel = label;
            this.cadence = cadence;
        }

        Builder alt(String... values) {
            alt = List.of(values);
            return this;
        }

        Builder status(String value) {
            status = value;
            return this;
        }

        Builder visibility(String value) {
            visibility = value;
            return this;
        }

        Builder age(String value) {
            age = value;
            return this;
        }

        Builder chapters(int count, Duration lastUpdate) {
            chapters = count;
            lastUpdateAgo = lastUpdate;
            return this;
        }

        Builder synopsis(String value) {
            synopsis = value;
            return this;
        }

        Builder halfAfter(int chapter) {
            halfAfter = chapter;
            return this;
        }

        Builder scheduled() {
            scheduled = true;
            return this;
        }

        Builder earlyAccess() {
            earlyAccess = true;
            return this;
        }

        Builder oldSlug(String value) {
            oldSlug = value;
            return this;
        }

        SeedSeries build() {
            int year = 2020 + Math.floorMod(slug.hashCode(), 7);
            return new SeedSeries(
                    slug,
                    title,
                    alt,
                    novel,
                    status,
                    visibility,
                    age,
                    genres,
                    color,
                    cadenceLabel,
                    cadence,
                    year,
                    chapters,
                    lastUpdateAgo,
                    synopsis,
                    halfAfter,
                    scheduled,
                    earlyAccess,
                    oldSlug);
        }
    }
}
