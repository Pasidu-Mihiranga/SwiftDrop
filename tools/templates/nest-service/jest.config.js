/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  // 151002: ts-jest notice about Node16 module kind; we compile to CommonJS so it does not apply.
  transform: { "^.+\\.ts$": ["ts-jest", { tsconfig: "<rootDir>/tsconfig.json", diagnostics: { ignoreCodes: [151002] } }] },
  // Test against the shared library's sources, so it does not need to be built first.
  moduleNameMapper: {
    "^@swiftdrop/common$": "<rootDir>/../../libs/node/swiftdrop-common/src",
    "^@swiftdrop/common/nest$": "<rootDir>/../../libs/node/swiftdrop-common/src/nest",
  },
  collectCoverageFrom: ["src/**/*.ts", "!src/main.ts", "!src/**/*.module.ts"],
  coverageReporters: ["text-summary", "lcov", "cobertura"],
  coverageThreshold: { global: { lines: 70 } },
};
