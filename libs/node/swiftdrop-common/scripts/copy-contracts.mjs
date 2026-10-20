// Bundles the event JSON Schemas into dist/contracts so validateEvent() works inside Docker images.
import { cpSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = join(here, "..", "..", "..", "..", "contracts", "events");
const target = join(here, "..", "dist", "contracts");
mkdirSync(target, { recursive: true });
cpSync(source, target, { recursive: true });
console.log(`Copied event contracts to ${target}`);
