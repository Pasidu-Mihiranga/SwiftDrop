import { defineConfig, devices } from "@playwright/test";
import { env } from "./support/env";

/**
 * The demo suite. It drives the real stack started by `pnpm stack:up`, nothing is mocked. Specs
 * run in file order (01-, 02-, ...) on one worker so the demo story reads top to bottom, and the
 * results land in doc/test-reports/e2e for the submission.
 */
export default defineConfig({
  testDir: "./specs",
  fullyParallel: false,
  workers: 1,
  // One retry absorbs a reset keep-alive socket; a test that only passes on retry is reported as flaky.
  retries: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  globalSetup: "./support/global-setup.ts",
  outputDir: "../../doc/test-reports/e2e/artifacts",
  reporter: [
    ["list"],
    ["html", { outputFolder: "../../doc/test-reports/e2e/html", open: "never" }],
    ["junit", { outputFile: "../../doc/test-reports/e2e/junit.xml" }],
    ["json", { outputFile: "../../doc/test-reports/e2e/results.json" }],
  ],
  use: {
    baseURL: env.webUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
