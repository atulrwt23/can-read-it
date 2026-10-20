import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes, NOVEL, signInWithCode, uniqueEmail } from "./support";

/** The API's local profile makes this address an admin (ADMIN_EMAILS). */
const ADMIN = "admin@example.com";
const CHAPTER = `/series/${NOVEL.slug}/chapter/2`;

test("a reader reports a comment and a moderator removes it", async ({ page, browser }) => {
  const text = `Buy cheap coins ${Date.now()}`;

  // A reader writes the comment...
  await signInWithCode(page, uniqueEmail("writer"));
  await page.goto(CHAPTER);
  const comments = page.getByRole("region", { name: "Comments" });
  await comments.scrollIntoViewIfNeeded();
  await comments.getByLabel("Add a comment").fill(text);
  await comments.getByRole("button", { name: "Post comment" }).click();
  await expect(comments.getByRole("listitem").filter({ hasText: text })).toBeVisible();

  // ...another reader reports it...
  const reporterContext = await browser.newContext();
  const reporter = await reporterContext.newPage();
  await signInWithCode(reporter, uniqueEmail("reporter"));
  await reporter.goto(CHAPTER);
  // Comments load when the section comes near the viewport.
  const section = reporter.getByRole("region", { name: "Comments" });
  await section.scrollIntoViewIfNeeded();
  const theirs = section.getByRole("listitem").filter({ hasText: text });
  await theirs.scrollIntoViewIfNeeded();
  await theirs.getByRole("button", { name: /^Report/ }).click();
  await theirs.getByLabel("Why are you reporting it?").selectOption("SPAM");
  await theirs.getByLabel(/Anything the moderators should know/).fill("Selling coins");
  await expectAccessibleInBothThemes(reporter);
  await theirs.getByRole("button", { name: "Send report" }).click();
  await expect(theirs.getByText("Thanks. A moderator will take a look.")).toBeVisible();

  // ...and a moderator removes it from the queue.
  const staffContext = await browser.newContext();
  const staff = await staffContext.newPage();
  await signInWithCode(staff, ADMIN);
  await staff.getByRole("button", { name: /^Account menu for / }).click();
  await staff.getByRole("link", { name: "Moderation" }).click();
  await expect(staff.getByRole("heading", { name: "Moderation", level: 1 })).toBeVisible();
  const card = staff.getByRole("listitem").filter({ hasText: text });
  await expect(card).toBeVisible();
  await expect(card.getByText("Spam × 1")).toBeVisible();
  await expect(card.getByText("“Selling coins”")).toBeVisible();
  await expectAccessibleInBothThemes(staff);
  await card.getByLabel("Note for the log (optional)").fill("Advertising");
  await card.getByRole("button", { name: "Remove comment" }).click();
  await expect(staff.getByText("The comment was removed.")).toBeVisible();
  await expect(card).toHaveCount(0);
  await expect(
    staff.getByRole("region", { name: "Recent decisions" }).getByText("“Advertising”").first(),
  ).toBeVisible();

  // Readers no longer see it.
  await reporter.reload();
  await reporter.getByRole("region", { name: "Comments" }).scrollIntoViewIfNeeded();
  await expect(reporter.getByText("Loading comments…")).toHaveCount(0);
  await expect(reporter.getByText(text)).toHaveCount(0);

  await reporterContext.close();
  await staffContext.close();
});

test("readers without a staff role can't open the moderation queue", async ({ page }) => {
  await signInWithCode(page, uniqueEmail("nosy"));
  await page.goto("/admin/moderation");
  await expect(page.getByRole("heading", { name: "No access" })).toBeVisible();
});
