import { expect, test } from "@playwright/test";
import { expectAccessibleInBothThemes, NOVEL, signInWithCode, uniqueEmail } from "./support";

const CHAPTER = `/series/${NOVEL.slug}/chapter/1`;

test("guests read comments and are invited to sign in to write one", async ({ page }) => {
  await page.goto(CHAPTER);
  const section = page.getByRole("region", { name: "Comments" });
  await section.scrollIntoViewIfNeeded();
  await expect(section.getByRole("link", { name: "Sign in to comment" })).toHaveAttribute(
    "href",
    `/login?next=${encodeURIComponent(CHAPTER)}`,
  );
  await expect(
    section.getByText(/^(No comments yet\. Be the first\.)$/).or(section.getByRole("list")),
  ).toBeVisible();
});

test("readers post a comment under a chapter and delete it", async ({ page }) => {
  await signInWithCode(page, uniqueEmail("comment"));
  await page.goto(CHAPTER);
  const section = page.getByRole("region", { name: "Comments" });
  await section.scrollIntoViewIfNeeded();

  const text = `Loved this chapter ${Date.now()}`;
  await section.getByLabel("Add a comment").fill(`  ${text}  `);
  await section.getByRole("button", { name: "Post comment" }).click();
  await expect(section.getByText("Your comment was posted.")).toHaveAttribute("role", "status");
  const mine = section.getByRole("listitem").filter({ hasText: text });
  await expect(mine).toBeVisible();
  await expect(section.getByLabel("Add a comment")).toHaveValue("");
  await expectAccessibleInBothThemes(page);

  // Still there for the next visitor.
  await page.reload();
  await page.getByRole("region", { name: "Comments" }).scrollIntoViewIfNeeded();
  await expect(mine).toBeVisible();

  await mine.getByRole("button", { name: "Delete your comment" }).click();
  await expect(section.getByText("Your comment was deleted.")).toHaveAttribute("role", "status");
  await expect(mine).toHaveCount(0);
});
