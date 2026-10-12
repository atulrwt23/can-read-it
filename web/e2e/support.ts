import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

/** Seeded placeholder series the tests rely on (backend LocalCatalogSeeder / SeedSeries). */
export const MANHWA = { slug: "tower-of-quiet-stars", title: "Tower of Quiet Stars" };
export const NOVEL = { slug: "a-map-of-unwritten-cities", title: "A Map of Unwritten Cities" };

/**
 * Fails on any WCAG 2.1 A/AA problem axe finds, in the current theme. Placeholder cover images are generated
 * art and are excluded from colour checks only by being images, which axe skips.
 */
export async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  const problems = results.violations.map(
    (violation) =>
      `${violation.id} (${violation.impact}): ${violation.help}\n  ${violation.nodes
        .slice(0, 3)
        .map((node) => node.target.join(" "))
        .join("\n  ")}`,
  );
  expect(problems, problems.join("\n")).toEqual([]);
}

/** Runs the accessibility check in the light theme, then again in the dark theme. */
export async function expectAccessibleInBothThemes(page: Page) {
  for (const theme of ["light", "dark"] as const) {
    await page.evaluate((value) => {
      document.documentElement.dataset.theme = value;
    }, theme);
    await expectAccessible(page);
  }
}
