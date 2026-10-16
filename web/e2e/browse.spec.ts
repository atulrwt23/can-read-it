import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes, MANHWA } from "./support";

test("filters are mirrored in the URL and can be cleared", async ({ page }) => {
  await page.goto("/browse");
  // While the page streams, React briefly keeps a hidden copy of the results; wait for the one shown.
  await expect(page.getByText(/^\d+ series$/).filter({ visible: true })).toHaveCount(1);
  // The filter bar is part of that copy too: act only once the stream has settled to one.
  await expect(page.getByLabel("Type")).toHaveCount(1);

  await page.getByLabel("Type").selectOption("NOVEL");
  await expect(page).toHaveURL(/type=NOVEL/);
  await expect(page.locator('section[aria-label="Results"] article').first()).toBeVisible();
  for (const badge of await page
    .locator('section[aria-label="Results"] article')
    .getByText("Manhwa")
    .all()) {
    await expect(badge).toBeHidden();
  }

  await page.getByRole("link", { name: "Clear filters" }).click();
  await expect(page).toHaveURL(/\/browse$/);
  await expectAccessibleInBothThemes(page);
});

test("search tolerates typos and offers suggestions", async ({ page }) => {
  await page.goto("/");
  const search = page.getByRole("combobox", { name: "Search series" });
  await search.fill("towr");
  await expect(page.getByRole("option", { name: new RegExp(MANHWA.title) })).toBeVisible();
  await page.getByRole("option", { name: /See all results/ }).click();

  await expect(page).toHaveURL(/\/browse\?q=towr/);
  await expect(page.getByRole("heading", { name: "Results for “towr”" })).toBeVisible();
  await expect(page.getByLabel("Sort")).toHaveValue("RELEVANCE");
  await expect(page.locator('section[aria-label="Results"] article').first()).toContainText(
    MANHWA.title,
  );
});
