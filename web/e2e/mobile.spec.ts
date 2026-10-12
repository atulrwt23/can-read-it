import { expect, test } from "@playwright/test";
import { expectAccessible, MANHWA } from "./support";

test("mobile header opens its menu", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Open menu" }).click();
  await expect(
    page.getByRole("navigation", { name: "Menu" }).getByRole("link", { name: "Browse" }),
  ).toBeVisible();
  await expectAccessible(page);
});

test("mobile reader has a bottom chapter bar", async ({ page }) => {
  await page.goto(`/series/${MANHWA.slug}/chapter/1`);
  const bottom = page.getByRole("navigation", { name: "Chapter navigation" });
  await expect(bottom.getByRole("link", { name: "Next ›" })).toBeVisible();
});
