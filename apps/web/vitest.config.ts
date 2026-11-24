import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(__dirname, "src") },
    // One React for the components and for react-dom. A loaded run once failed whole test files with
    // "Cannot read properties of null (reading 'useState')": react-dom set the hook dispatcher on one
    // instance of react@19.3.1 while the components read another.
    dedupe: ["react", "react-dom"],
  },
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
        // Components owned by other milestones or browser-only widgets. Their logic is
        // covered by unit tests in src/lib/*.test.ts (e.g. reports.test.ts).
        "src/components/track-form.tsx",
        "src/components/site-header.tsx",
        "src/components/area-dashboard.tsx",
        "src/components/location-picker.tsx",
        "src/components/notification-bell.tsx",
        "src/components/ai-assistant-widget.tsx",
        "src/components/reports/**",
      ],
      reporter: ["text-summary", "lcov", "cobertura"],
      thresholds: { lines: 70, branches: 70, functions: 70, statements: 70 },
    },
  },
});
