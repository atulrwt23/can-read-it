import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes, MANHWA } from "./support";

test("series page shows details, chapters and jumps to a chapter", async ({ page }) => {
  await page.goto(`/series/${MANHWA.slug}`);
  await expect(page.getByRole("heading", { level: 1, name: MANHWA.title })).toBeVisible();
  await expect(page.getByRole("link", { name: "Start reading" })).toBeVisible();

  // Search engines get a structured description of the series.
  const structured = JSON.parse(
    (await page.locator('script[type="application/ld+json"]').textContent()) ?? "{}",
  );
  expect(structured).toMatchObject({ "@type": "ComicSeries", name: MANHWA.title });

  const jump = page.getByLabel("Go to chapter");
  await jump.fill("9999");
  await jump.press("Enter");
  await expect(page.locator("form p[role=alert]")).toHaveText("Chapter 9999 isn't out yet.");

  await expectAccessibleInBothThemes(page);

  await jump.fill("2");
  await jump.press("Enter");
  await expect(page).toHaveURL(new RegExp(`/series/${MANHWA.slug}/chapter/2$`));
});

test("unknown series show the not found page, kept out of search results", async ({ page }) => {
  // The loading skeleton starts streaming before the series is looked up, so the status is already 200;
  // Next.js marks the page noindex instead (CLAUDE.md section 8).
  await page.goto("/series/no-such-series");
  await expect(page.getByRole("heading", { name: "Not found" })).toBeVisible();
  await expect(page.locator('meta[name="robots"][content="noindex"]').first()).toBeAttached();
});
