import { crc32, deflateSync } from "node:zlib";
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

/** How many emails Mailpit holds for this address, so a later {@link codeFromInbox} can skip them. */
export async function inboxCount(email: string): Promise<number> {
  const search = await fetch(
    `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}`,
  );
  const found = (await search.json()) as { messages: unknown[] };
  return found.messages.length;
}

/** Signs in with an emailed code (creating the account the first time), which also confirms the email. */
export async function signInWithCode(page: Page, email: string) {
  const before = await inboxCount(email);
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByRole("button", { name: "Email me a code" }).click();
  await page.getByLabel("6-digit code").fill(await codeFromInbox(email, before));
  await page.getByRole("button", { name: /^(Sign in|Create account)$/ }).click();
  await expect(page.getByRole("button", { name: /^Account menu for / })).toBeVisible();
}

/** A flat-colour PNG, built here so the tests need no image files. */
export function solidPng(width: number, height: number, rgb: [number, number, number]): Buffer {
  const row = Buffer.alloc(1 + width * 3);
  for (let x = 0; x < width; x++) row.set(rgb, 1 + x * 3);
  const raw = Buffer.concat(Array.from({ length: height }, () => row));
  const chunk = (type: string, data: Buffer) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header.set([8, 2, 0, 0, 0], 8); // 8-bit RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
