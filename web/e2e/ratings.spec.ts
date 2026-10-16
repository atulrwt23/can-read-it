import { expect, test } from "@playwright/test";
import {
  expectAccessibleInBothThemes,
  MANHWA,
  NOVEL,
  signInWithCode,
  uniqueEmail,
} from "./support";

test("guests see the average rating and are invited to sign in to rate", async ({ page }) => {
  await page.goto(`/series/${MANHWA.slug}`);
  await expect(page.getByText(/^\([\d.,K]+ ratings?\)$/)).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign in to rate this series" })).toHaveAttribute(
    "href",
    `/login?next=${encodeURIComponent(`/series/${MANHWA.slug}`)}`,
  );
});

test("readers rate a series, change their mind and remove the rating", async ({ page }) => {
  await signInWithCode(page, uniqueEmail("rating"));
  await page.goto(`/series/${NOVEL.slug}`);
  const stars = page.getByRole("radiogroup", { name: "Your rating" });
  await expect(stars).toBeVisible();
  await expectAccessibleInBothThemes(page);

  const star = (n: number) =>
    stars.locator("label", { has: page.getByRole("radio", { name: `${n} stars` }) });
  await star(4).click();
  await expect(page.getByText("You rated it 4 out of 5.")).toHaveAttribute("role", "status");
  await expect(stars.getByRole("radio", { name: "4 stars" })).toBeChecked();

  await page.reload();
  await expect(stars.getByRole("radio", { name: "4 stars" })).toBeChecked();
  await star(2).click();
  await expect(page.getByText("You rated it 2 out of 5.")).toHaveAttribute("role", "status");

  await page.getByRole("button", { name: "Remove rating" }).click();
  await expect(page.getByText("Your rating was removed.")).toHaveAttribute("role", "status");
  await expect(stars.getByRole("radio", { checked: true })).toHaveCount(0);
});
