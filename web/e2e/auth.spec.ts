import { expect, type Page, test } from "@playwright/test";
import { codeFromInbox, expectAccessibleInBothThemes, uniqueEmail } from "./support";

const PASSWORD = "a long enough passphrase";

async function signUpWithPassword(page: Page, email: string, handle: string) {
  await page.goto("/signup");
  await page.getByText("With a password").click();
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Handle").fill(handle);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("heading", { name: "Check your email" })).toBeVisible();
  await page.getByLabel("6-digit code").fill(await codeFromInbox(email));
  await page.getByRole("button", { name: "Confirm and sign in" }).click();
  await expect(page).toHaveURL("/");
}

function handleFor(label: string) {
  return `${label}_${Date.now().toString(36)}`.slice(0, 30);
}

test("sign-in and sign-up pages are accessible", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  // No provider is configured locally, so there are no Google or Apple buttons.
  await expect(page.getByRole("button", { name: "Continue with Apple" })).toHaveCount(0);
  await expectAccessibleInBothThemes(page);

  await page.getByText("With a password").click();
  await expect(page.getByLabel("Password", { exact: true })).toBeVisible();
  await expectAccessibleInBothThemes(page);

  await page.goto("/signup");
  await expect(page.getByRole("heading", { name: "Create an account" })).toBeVisible();
  await expectAccessibleInBothThemes(page);

  await page.goto("/login/reset");
  await expect(page.getByRole("heading", { name: "Reset your password" })).toBeVisible();
  await expectAccessibleInBothThemes(page);
});

test("signs up with a password, manages the account and signs out", async ({ page }) => {
  const email = uniqueEmail("password");
  const handle = handleFor("pw");
  await signUpWithPassword(page, email, handle);

  // The header now shows the account menu.
  const menu = page.getByRole("button", { name: `Account menu for ${handle}` });
  await expect(menu).toBeVisible();
  await menu.click();
  await page.getByRole("link", { name: "Account" }).click();

  await expect(page.getByRole("heading", { name: "Account", level: 1 })).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();
  await expect(page.getByText("Confirmed")).toBeVisible();
  await expect(page.getByText("This device")).toBeVisible();
  await expectAccessibleInBothThemes(page);

  await page.getByLabel("Display name").fill("Ana Reads");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Saved." })).toBeVisible();
  await expect(page.getByRole("button", { name: "Account menu for Ana Reads" })).toBeVisible();

  await page.getByRole("button", { name: "Account menu for Ana Reads" }).click();
  await page
    .locator("#account-menu")
    .getByRole("button", { name: "Sign out", exact: true })
    .click();
  await expect(page.getByRole("link", { name: "Sign in" }).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Sign in to see your account" })).toBeVisible();

  // And back in with the password.
  await page.goto("/login?next=/account");
  await page.getByText("With a password").click();
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL("/account");
  await expect(page.getByLabel("Display name")).toHaveValue("Ana Reads");
});

test("signs up and in with an emailed code", async ({ page }) => {
  const email = uniqueEmail("code");
  const handle = handleFor("code");

  await page.goto("/signup");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Handle").fill(handle);
  await page.getByRole("button", { name: "Email me a code" }).click();
  await page.getByLabel("6-digit code").fill(await codeFromInbox(email));
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("button", { name: `Account menu for ${handle}` })).toBeVisible();

  // Sign out, then back in with a new code.
  await page.getByRole("button", { name: `Account menu for ${handle}` }).click();
  await page
    .locator("#account-menu")
    .getByRole("button", { name: "Sign out", exact: true })
    .click();
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByRole("button", { name: "Email me a code" }).click();
  await expect(page.getByRole("heading", { name: "Check your email" })).toBeVisible();
  await page.getByLabel("6-digit code").fill(await codeFromInbox(email, 1));
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("button", { name: `Account menu for ${handle}` })).toBeVisible();
});

test("a wrong password says so without saying whether the email exists", async ({ page }) => {
  await page.goto("/login");
  await page.getByText("With a password").click();
  await page.getByLabel("Email", { exact: true }).fill(uniqueEmail("nobody"));
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(
    page.getByRole("alert").filter({ hasText: "The email or password is wrong" }),
  ).toBeVisible();
});

test("deleting the account signs out and shows when it is erased", async ({ page }) => {
  const email = uniqueEmail("delete");
  await signUpWithPassword(page, email, handleFor("del"));

  await page.goto("/account");
  await page.getByRole("button", { name: "Delete account…" }).click();
  await page.getByLabel("Your password, to confirm").fill(PASSWORD);
  await page.getByRole("button", { name: "Delete my account" }).click();
  await expect(page.getByRole("heading", { name: "Your account will be deleted" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign in" }).first()).toBeVisible();
});

test("an expired access token is renewed without signing out", async ({ page, context }) => {
  const handle = handleFor("renew");
  await signUpWithPassword(page, uniqueEmail("renew"), handle);

  // The access cookie lives 15 minutes; dropping it is what the browser does when it expires.
  await context.clearCookies({ name: "access_token" });
  await page.goto("/account");

  await expect(page.getByRole("button", { name: `Account menu for ${handle}` })).toBeVisible();
  await expect(page.getByText("This device")).toBeVisible();
  const cookies = await context.cookies();
  expect(cookies.some((cookie) => cookie.name === "access_token")).toBe(true);
});
