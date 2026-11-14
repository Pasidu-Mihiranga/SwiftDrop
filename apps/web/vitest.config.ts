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
      // Shared library code and the interactive components, so component tests count
      // towards the gate rather than running outside it.
      include: ["src/lib/**/*.ts", "src/components/**/*.tsx"],
      exclude: [
        // Server-only data entry points: thin wrappers around fetch, exercised by the
        // services' own contract tests rather than here.
        "src/lib/gateway.ts",
        "src/lib/pricing-api.ts",
        // Components owned by other milestones. They have no tests yet, and the web gate
        // should measure the code each milestone actually wrote. When a milestone adds
        // tests for its component, delete its line from this list.
        "src/components/track-form.tsx",
        "src/components/site-header.tsx",
        "src/components/area-dashboard.tsx",
      ],
      reporter: ["text-summary", "lcov", "cobertura"],
      thresholds: { lines: 70, branches: 70, functions: 70, statements: 70 },
    },
  },
});
