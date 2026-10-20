/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  // 151002: ts-jest notice about Node16 module kind; we compile to CommonJS so it does not apply.
  transform: { "^.+\\.ts$": ["ts-jest", { tsconfig: "<rootDir>/tsconfig.json", diagnostics: { ignoreCodes: [151002] } }] },
  collectCoverageFrom: ["src/**/*.ts", "!src/**/index.ts"],
  coverageReporters: ["text-summary", "lcov", "cobertura"],
  coverageThreshold: { global: { lines: 70 } },
};
