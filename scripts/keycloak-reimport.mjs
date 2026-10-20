#!/usr/bin/env node
// Re-imports infra/keycloak/realm-swiftdrop.json after you change it (or after setting Google credentials).
// Keycloak only imports a realm that does not exist yet, so this recreates Keycloak's database.
// WARNING: users created through the UI (not in the realm file) are deleted.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const env = Object.fromEntries(
  readFileSync(join(root, ".env"), "utf8")
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]),
);

const compose = (...args) => execFileSync("docker", ["compose", "--profile", "infra", ...args], { cwd: root, stdio: "inherit" });

console.log("Stopping Keycloak ...");
compose("rm", "-sf", "keycloak");
console.log("Recreating the keycloak database ...");
compose("exec", "-T", "postgres", "psql", "-U", env.POSTGRES_USER, "-d", "postgres",
  "-c", "DROP DATABASE IF EXISTS keycloak WITH (FORCE)",
  "-c", "CREATE DATABASE keycloak OWNER keycloak");
console.log("Starting Keycloak (imports the realm on start) ...");
compose("up", "-d", "keycloak");
