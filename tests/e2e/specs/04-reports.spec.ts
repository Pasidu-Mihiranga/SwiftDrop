import { expect, test } from "@playwright/test";
import { businessToday, gatewayAs, signIn } from "../support/api";
import { users } from "../support/env";

const today = businessToday();
const daily = `/api/v1/reports/daily?from=${today}&to=${today}`;

test.describe("Reports authorization", () => {
  for (const [role, user] of [
    ["customer", users.customer],
    ["courier", users.courier],
    ["merchant", users.merchant],
  ] as const) {
    test(`a ${role} gets 403 from the report API`, async () => {
      const response = await (await gatewayAs(user)).get(daily);
      expect(response.status()).toBe(403);
    });
  }

  test("a guest must sign in", async () => {
    const response = await (await gatewayAs()).get(daily);
    expect(response.status()).toBe(401);
  });

  test("a dispatcher may read reports but not rebuild them", async () => {
    const api = await gatewayAs(users.dispatcher);
    expect((await api.get(daily)).status()).toBe(200);
    expect((await api.post("/api/v1/reports/rebuild")).status()).toBe(403);
  });

  test("a customer is kept out of the reports page", async ({ page }) => {
    await signIn(page, users.customer, "/customer");
    await page.goto("/admin/reports");
    await expect(page).toHaveURL(/\/forbidden/);
  });
});

test.describe("Reports dashboard", () => {
  test("an admin sees deliveries per day and the success rate", async ({ page }) => {
    await signIn(page, users.admin, "/admin");
    await page.getByRole("link", { name: /Delivery reports/ }).click();
    await expect(page.getByRole("heading", { name: "Delivery reports" })).toBeVisible();
    await expect(page.getByRole("meter", { name: "Success rate" })).toBeVisible();
    await expect(page.getByRole("tab", { name: "Deliveries" })).toBeVisible();
    await page.getByRole("button", { name: "Show table" }).click();
    await expect(page.getByRole("table", { name: "Parcels per day" })).toBeVisible();
  });

  test("the dashboard matches what the report API returns", async ({ page }) => {
    const api = await gatewayAs(users.admin);
    const from = new Date(Date.now() - 6 * 86_400_000).toLocaleDateString("en-CA", { timeZone: "Asia/Colombo" });
    const report = await (await api.get(`/api/v1/reports/daily?from=${from}&to=${today}`)).json();

    await signIn(page, users.admin, "/admin");
    await page.goto("/admin/reports?range=7d");
    const { created, delivered, failed, cancelled } = report.totals;
    if (created + delivered + failed + cancelled === 0) {
      // A fresh stack: the dashboard must say so rather than draw empty charts.
      await expect(page.getByText("No parcel activity in this period")).toBeVisible();
      return;
    }
    const summary = page.getByRole("region", { name: "Summary" });
    const count = (n: number) => new Intl.NumberFormat("en-US").format(n);
    await expect(summary).toContainText(count(delivered));
    await expect(summary).toContainText(count(created));
    if (report.totals.successRate !== null) {
      await expect(page.getByRole("meter", { name: "Success rate" })).toHaveAttribute(
        "aria-valuenow",
        String(Math.round(report.totals.successRate * 1000) / 10),
      );
    }
  });
});
