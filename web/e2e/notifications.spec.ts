import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes, signInWithCode, uniqueEmail } from "./support";

test("readers turn new-chapter emails off and on in their account", async ({ page }) => {
  await signInWithCode(page, uniqueEmail("emails"));
  await page.goto("/account");
  const emails = page.getByRole("region", { name: "Emails" });
  const toggle = emails.getByRole("checkbox", { name: "New chapters of series I follow" });
  await expect(toggle).toBeChecked();
  await expectAccessibleInBothThemes(page);

  await toggle.uncheck();
  await expect(emails.getByRole("status")).toHaveText(/New-chapter emails are off\./);
  await page.reload();
  await expect(toggle).not.toBeChecked();

  await toggle.check();
  await expect(emails.getByRole("status")).toHaveText(/New-chapter emails are on\./);
});

test("the unsubscribe page waits for a click and explains a bad link", async ({ page }) => {
  await page.goto("/unsubscribe");
  await expect(page.getByRole("heading", { name: "This link doesn't work" })).toBeVisible();

  // A well-formed token nobody has: the button is shown, and the API says no.
  await page.goto(`/unsubscribe?token=${"a".repeat(32)}`);
  await expect(page.getByRole("heading", { name: "Stop new-chapter emails" })).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await expectAccessibleInBothThemes(page);
  await page.getByRole("button", { name: "Stop new-chapter emails" }).click();
  await expect(page.getByRole("heading", { name: "This link doesn't work" })).toBeVisible();
  await expectAccessibleInBothThemes(page);
});
