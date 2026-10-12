import { expect, test } from "@playwright/test";
import { expectAccessible, MANHWA, NOVEL } from "./support";

test("manhwa reader shows sized pages and moves between chapters", async ({ page }) => {
  await page.goto(`/series/${MANHWA.slug}/chapter/1`);
  const pages = page.locator("main img");
  await expect(pages.first()).toBeVisible();
  // Every page carries its size, so the layout never shifts while images load.
  for (const img of await pages.all()) {
    expect(Number(await img.getAttribute("width"))).toBeGreaterThan(0);
    expect(Number(await img.getAttribute("height"))).toBeGreaterThan(0);
  }

  const picker = page.getByLabel("Chapter", { exact: true });
  await expect(picker).toHaveValue("1");
  await expectAccessible(page);

  // Reaching the end marks the chapter read and offers the next one.
  await page.locator("#chapter-end").scrollIntoViewIfNeeded();
  await page.getByRole("link", { name: /^Next: Ch\./ }).click();
  await expect(page).toHaveURL(new RegExp(`/series/${MANHWA.slug}/chapter/2$`));
  await expect(picker).toHaveValue("2");

  await page.goto(`/series/${MANHWA.slug}`);
  await expect(page.getByRole("img", { name: "Read" }).first()).toBeVisible();
});

test("novel reader applies reading settings", async ({ page }) => {
  await page.goto(`/series/${NOVEL.slug}/chapter/1`);
  const body = page.locator("main article");
  await expect(body).toBeVisible();
  const before = await body.evaluate((el) => getComputedStyle(el).fontSize);

  await page.getByRole("button", { name: "Reading settings" }).click();
  await page.getByRole("button", { name: "Larger text" }).click();
  await expect.poll(() => body.evaluate((el) => getComputedStyle(el).fontSize)).not.toBe(before);
  await page.keyboard.press("Escape");

  await expectAccessible(page);
});
