import { expect, test } from "@playwright/test";

test.describe("Public pages", () => {
  test("home page loads with tracking and prices in the menu", async ({ page }) => {
    await page.goto("/");
    // The landing page also has footer link groups, so name the header's navigation.
    const nav = page.getByRole("navigation", { name: "Primary" });
    await expect(nav.getByRole("link", { name: "Track a parcel" })).toBeVisible();
    await expect(nav.getByRole("link", { name: "Prices" })).toBeVisible();
  });

  test("a guest gets a real fee estimate from pricing-service", async ({ page }) => {
    await page.goto("/quote");
    await page.getByLabel("Collect from").selectOption("Colombo");
    await page.getByLabel("Deliver to").selectOption("Kandy");
    await page.getByLabel("Weight (kg)").fill("2");
    await page.getByRole("button", { name: "Get estimate" }).click();
    await expect(page.getByText(/^LKR [\d,]+\.\d\d$/).first()).toBeVisible();
  });
});
