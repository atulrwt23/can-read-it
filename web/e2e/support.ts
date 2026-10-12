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

const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";

/** A fresh address for each test run, so runs never collide. */
export function uniqueEmail(label: string): string {
  return `${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

/**
 * The 6-digit code in the newest email to this address, from Mailpit (the local SMTP catcher). Account emails go
 * out asynchronously, so this waits for one to arrive; `after` skips emails that were already there.
 */
export async function codeFromInbox(email: string, after = 0): Promise<string> {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const search = await fetch(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`,
    );
    const found = (await search.json()) as { messages: { ID: string }[] };
    const newest = found.messages[0];
    if (newest && found.messages.length > after) {
      const message = await fetch(`${MAILPIT}/api/v1/message/${newest.ID}`);
      const { Text } = (await message.json()) as { Text: string };
      const code = Text.match(/\b(\d{6})\b/)?.[1];
      if (code) return code;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`No code arrived for ${email}`);
}
