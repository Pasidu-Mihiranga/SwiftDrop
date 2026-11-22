import { type Page, expect, test } from "@playwright/test";
import { gatewayAs, signIn } from "../support/api";
import { users } from "../support/env";

/** Whether an LLM key is configured. Without one the widget must say so instead of failing. */
async function llmConfigured(): Promise<boolean> {
  const api = await gatewayAs();
  const response = await api.get("/api/v1/assistant/meta/provider");
  expect(response.ok()).toBeTruthy();
  return ((await response.json()) as { configured: boolean }).configured;
}

async function ask(page: Page, question: string) {
  await page.getByRole("button", { name: "Ask the delivery assistant" }).click();
  const dialog = page.getByRole("dialog", { name: "Delivery assistant" });
  await dialog.getByRole("textbox", { name: "Message" }).fill(question);
  await dialog.getByRole("button", { name: "Send", exact: true }).click();
  return dialog;
}

test.describe("AI delivery assistant (chat widget)", () => {
  test("the widget is on every page", async ({ page }) => {
    for (const path of ["/", "/quote", "/track"]) {
      await page.goto(path);
      await expect(page.getByRole("button", { name: "Ask the delivery assistant" })).toBeVisible();
    }
  });

  test("a guest asks for a price and gets the real quote", async ({ page }) => {
    const configured = await llmConfigured();
    await page.goto("/");
    const dialog = await ask(page, "How much to send 2 kg from Colombo to Kandy?");
    const log = dialog.getByRole("log", { name: "Conversation" });

    if (!configured) {
      // No GEMINI_API_KEY / OPENAI_API_KEY in this environment: the graceful message is the contract.
      await expect(log).toContainText("not switched on here yet");
      return;
    }
    await expect(log.getByText("Estimated the delivery fee")).toBeVisible({ timeout: 45_000 });
    await expect(log).toContainText(/LKR|Rs/, { timeout: 45_000 });
  });

  test("a signed-in customer asks where their parcel is", async ({ page }) => {
    test.skip(!(await llmConfigured()), "No LLM key is configured, so the assistant cannot answer.");
    await signIn(page, users.customer, "/customer");
    const dialog = await ask(page, "Where is my parcel?");
    const log = dialog.getByRole("log", { name: "Conversation" });
    await expect(log.getByText("Listed your parcels")).toBeVisible({ timeout: 45_000 });
  });
});
