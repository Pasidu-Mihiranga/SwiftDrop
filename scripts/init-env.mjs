#!/usr/bin/env node
// Creates .env from .env.example, replacing "__generate__" placeholders with random secrets.
// Existing .env files are never overwritten (pass --force to regenerate).
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const source = join(root, ".env.example");
const target = join(root, ".env");
const force = process.argv.includes("--force");

if (existsSync(target) && !force) {
  console.log(".env already exists — leaving it untouched (use --force to regenerate).");
  process.exit(0);
}

const secret = () => randomBytes(24).toString("base64url");
const key32 = () => randomBytes(32).toString("base64");

const output = readFileSync(source, "utf8")
  .replaceAll("__generate_base64_32__", () => key32())
  .replaceAll("__generate__", () => secret());

writeFileSync(target, output, { mode: 0o600 });
console.log("Created .env with freshly generated secrets.");
