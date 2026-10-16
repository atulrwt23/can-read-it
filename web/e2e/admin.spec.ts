import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes, signInWithCode, solidPng } from "./support";

/** The API's local profile makes this address an admin (ADMIN_EMAILS). */
const ADMIN = "admin@example.com";

test("staff pages ask visitors to sign in", async ({ page }) => {
  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Sign in to continue" })).toBeVisible();
  await expectAccessibleInBothThemes(page);
});

test("an admin creates a series, uploads pages and releases a chapter", async ({ page }) => {
  const title = `Test Series ${Date.now().toString(36)}`;
  await signInWithCode(page, ADMIN);

  await page.getByRole("button", { name: /^Account menu for / }).click();
  await page.getByRole("link", { name: "Catalog" }).click();
  await expect(page.getByRole("heading", { name: "Catalog", level: 1 })).toBeVisible();
  await expect(page.getByText(/^\d+ series$/)).toBeVisible();
  await expectAccessibleInBothThemes(page);

  await page.getByRole("link", { name: "New series" }).click();
  await page.getByLabel("Title", { exact: true }).fill(title);
  await page.getByLabel("Visibility").selectOption("PUBLISHED");
  await page.getByLabel("Synopsis").fill("An invented placeholder story for the smoke tests.");
  await page.getByLabel("Cover").setInputFiles({
    name: "cover.png",
    mimeType: "image/png",
    buffer: solidPng(400, 600, [72, 95, 199]),
  });
  await expect(page.getByRole("img", { name: "Current cover" })).toBeVisible();
  await expectAccessibleInBothThemes(page);
  await page.getByRole("button", { name: "Create series" }).click();

  await expect(page).toHaveURL(/\/admin\/series\/[0-9a-f-]{36}$/);
  await expect(page.getByRole("heading", { name: "Chapters" })).toBeVisible();
  await expect(page.getByLabel("Number")).toHaveValue("1");
  await page.getByRole("button", { name: "Add chapter" }).click();

  await expect(page).toHaveURL(/\/admin\/chapters\/[0-9a-f-]{36}$/);
  await page.getByLabel("Add pages").setInputFiles([
    { name: "page-2.png", mimeType: "image/png", buffer: solidPng(300, 500, [0, 122, 112]) },
    { name: "page-1.png", mimeType: "image/png", buffer: solidPng(300, 400, [54, 54, 54]) },
  ]);
  await expect(page.getByRole("img", { name: "Page 2" })).toBeVisible();
  await page.getByRole("button", { name: "Move page 2 earlier" }).click();
  await page.getByRole("button", { name: "Save pages" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Pages saved." })).toBeVisible();
  await expectAccessibleInBothThemes(page);

  await page.getByRole("button", { name: "Release now" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Released." })).toBeVisible();
  await page.getByRole("link", { name: "Open in the reader" }).click();

  await expect(page).toHaveURL(/\/series\/test-series-[a-z0-9]+\/chapter\/1$/);
  const pages = page.locator("main img");
  await expect(pages).toHaveCount(2);
  // Page 2 was moved first: the 500 px tall image leads.
  await expect(pages.first()).toHaveAttribute("height", "500");
});

test("staff see anonymous daily counts", async ({ page }) => {
  // A visit to count: leaving the page sends its events.
  await page.goto("/browse");
  await page.goto("/");
  await signInWithCode(page, ADMIN);

  await page.goto("/admin/analytics");
  await expect(page.getByRole("heading", { name: "Analytics", level: 1 })).toBeVisible();
  const table = page.getByRole("table", { name: "Events per day" });
  await expect(table.getByRole("columnheader", { name: "Page views" })).toBeVisible();
  // Events arrive within seconds; today's row is counted live.
  await expect
    .poll(
      async () => {
        await page.reload();
        const total = await page
          .getByRole("table", { name: "Events per day" })
          .locator("tfoot td")
          .first()
          .textContent();
        return Number((total ?? "0").replace(/,/g, ""));
      },
      { timeout: 20_000, intervals: [1000, 2000, 3000] },
    )
    .toBeGreaterThan(0);
  await expectAccessibleInBothThemes(page);
});
