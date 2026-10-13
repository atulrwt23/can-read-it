import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes } from "./support";

test("home shows featured, latest updates and rankings", async ({ page }) => {
  await page.goto("/");

  const carousel = page.getByRole("region", { name: "Featured series" });
  await expect(carousel).toBeVisible();
  await expect(page.getByRole("heading", { name: "Latest updates" })).toBeVisible();
  const latest = page.locator('section[aria-labelledby="latest-title"] article');
  await expect(latest.first()).toBeVisible();
  // The header marks the current section.
  await expect(
    page.getByRole("navigation", { name: "Primary" }).getByRole("link", { name: "Home" }),
  ).toHaveAttribute("aria-current", "page");

  // The carousel moves with its buttons.
  const slide = carousel.locator('[aria-roledescription="slide"]');
  const first = await slide.getAttribute("aria-label");
  await carousel.getByRole("button", { name: "Next slide" }).click();
  await expect(slide).not.toHaveAttribute("aria-label", first ?? "");

  // Ranking tabs switch panels.
  const week = page.getByRole("tab", { name: "Week" });
  await week.click();
  await expect(week).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("tabpanel").locator("li").first()).toBeVisible();

  await expectAccessibleInBothThemes(page);
});

test("latest updates load more after the first page", async ({ page }) => {
  await page.goto("/");
  const latest = page.locator('section[aria-labelledby="latest-title"] article');
  await expect(latest).toHaveCount(12);
  await page
    .locator('section[aria-labelledby="latest-title"]')
    .getByRole("button", { name: "Load more" })
    .click();
  await expect.poll(() => latest.count()).toBeGreaterThan(12);
});

test("view all links lead to browse", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "View all popular series" }).click();
  await expect(page).toHaveURL(/\/browse\?sort=POPULAR$/);
  await expect(page.getByLabel("Sort")).toHaveValue("POPULAR");
});

test("site icons and the sitemap are served", async ({ request }) => {
  for (const path of ["/favicon.ico", "/icon.png", "/apple-icon.png"]) {
    expect((await request.get(path)).status(), path).toBe(200);
  }
  const sitemap = await request.get("/sitemap.xml");
  expect(sitemap.status()).toBe(200);
  expect(await sitemap.text()).toContain("/series/tower-of-quiet-stars</loc>");
  // The beta stays out of search engines.
  expect(await (await request.get("/robots.txt")).text()).toContain("Disallow: /");
});
