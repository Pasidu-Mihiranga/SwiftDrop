import { existsSync } from "node:fs";
import path from "node:path";
import type { NextConfig } from "next";

// Local development: reuse the repository's .env (Docker/Kubernetes inject real env vars instead).
const rootEnv = path.join(__dirname, "..", "..", ".env");
if (existsSync(rootEnv)) process.loadEnvFile(rootEnv);

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(self), geolocation=(self), microphone=()" },
];

const config: NextConfig = {
  output: "standalone",
  // Monorepo: trace dependencies from the repository root so the standalone build is complete.
  outputFileTracingRoot: path.join(__dirname, "..", ".."),
  poweredByHeader: false,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default config;
