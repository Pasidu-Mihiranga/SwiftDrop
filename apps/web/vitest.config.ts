import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: { alias: { "@": path.resolve(__dirname, "src") } },
  test: {
    // jsdom so component tests can render; Node globals are still available for the lib tests.
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    coverage: {
      provider: "v8",
      include: ["src/lib/**/*.ts"],
      // Server-only modules are exercised by the pages, not by unit tests.
      exclude: ["src/lib/gateway.ts", "src/lib/pricing-api.ts", "src/lib/pricing-actions.ts"],
      reporter: ["text-summary", "lcov", "cobertura"],
      thresholds: { lines: 70 },
    },
  },
});
