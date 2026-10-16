import { expect, test } from "@playwright/test";
import {
  expectAccessibleInBothThemes,
  MANHWA,
  NOVEL,
  signInWithCode,
  uniqueEmail,
} from "./support";

test("guests see what they read and are invited to sign in", async ({ page }) => {
  await page.goto(`/series/${MANHWA.slug}/chapter/1`);
  await expect(page.locator("main img").first()).toBeVisible();

  await page.goto("/library");
  await expect(page.getByRole("heading", { name: "Library", level: 1 })).toBeVisible();
  await expect(page.getByRole("main").getByRole("link", { name: "Sign in" })).toBeVisible();
  const history = page.getByRole("region", { name: "History" });
  await expect(history.getByText(MANHWA.title)).toBeVisible();
  await expectAccessibleInBothThemes(page);

  // Following needs an account.
  await page.goto(`/series/${MANHWA.slug}`);
  await expect(page.getByRole("link", { name: "Follow" })).toHaveAttribute(
    "href",
    `/login?next=${encodeURIComponent(`/series/${MANHWA.slug}`)}`,
  );
});

test("signing in keeps the guest history, and followed series fill the library", async ({
  page,
}) => {
  // Read as a guest first.
  await page.goto(`/series/${NOVEL.slug}/chapter/1`);
  await expect(page.locator("main article")).toBeVisible();

  await signInWithCode(page, uniqueEmail("library"));
  await page.goto("/library");
  await expect(page.getByRole("region", { name: "History" }).getByText(NOVEL.title)).toBeVisible();
  await expect(page.getByText("You don't follow any series yet.")).toBeVisible();

  await page.goto(`/series/${NOVEL.slug}`);
  await expect(page.getByRole("link", { name: "Continue Ch. 1" })).toBeVisible();
  const follow = page.getByRole("button", { name: "Follow" });
  await follow.click();
  await expect(page.getByRole("button", { name: "Following" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );

  await page.goto("/library");
  const following = page.getByRole("region", { name: "Following" });
  await expect(following.getByRole("link", { name: NOVEL.title }).first()).toBeVisible();
  await expect(following.getByRole("link", { name: "Continue Ch. 1" })).toBeVisible();
  await expectAccessibleInBothThemes(page);
});
