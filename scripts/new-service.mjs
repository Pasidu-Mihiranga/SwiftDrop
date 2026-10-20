#!/usr/bin/env node
/**
 * Scaffolds a new SwiftDrop microservice from tools/templates/<type>-service so every service
 * starts with the same layout, security, logging, tests and Dockerfile.
 *
 *   node scripts/new-service.mjs --type java --name loyalty-service --port 8087 --route loyalty \
 *        --db loyalty_db --db-user loyalty_svc --description "Loyalty points" [--kafka] [--redis]
 *
 *   node scripts/new-service.mjs --type nest --name chat-service --port 8093 --route chat \
 *        --description "Customer chat" [--kafka] [--mongo] [--redis]
 *
 * Templates use __service__, __pkg__, __Class__, __port__, __route__, __db__, __dbuser__,
 * __description__ placeholders and {{#flag}}...{{/flag}} optional sections (kafka, redis, mongo).
 */
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

const { values: args } = parseArgs({
  options: {
    type: { type: "string", default: "java" },
    name: { type: "string" },
    port: { type: "string" },
    route: { type: "string" },
    db: { type: "string", default: "" },
    "db-user": { type: "string", default: "" },
    description: { type: "string", default: "" },
    kafka: { type: "boolean", default: false },
    redis: { type: "boolean", default: false },
    mongo: { type: "boolean", default: false },
  },
});

function fail(message) {
  console.error(`✖ ${message}`);
  process.exit(1);
}

if (!args.name || !/^[a-z][a-z0-9-]*-service$/.test(args.name)) fail("--name must be kebab-case and end with -service");
if (!args.port || !/^\d{4,5}$/.test(args.port)) fail("--port is required (e.g. 8087)");
if (!args.route || !/^[a-z][a-z0-9-]*$/.test(args.route)) fail("--route is required (e.g. loyalty)");
if (args.type === "java" && (!args.db || !args["db-user"])) fail("--db and --db-user are required for java services");

const templateDir = join(root, "tools", "templates", `${args.type}-service`);
if (!existsSync(templateDir)) fail(`unknown --type ${args.type} (no ${relative(root, templateDir)})`);

const targetDir = join(root, "services", args.name);
if (existsSync(targetDir)) fail(`${relative(root, targetDir)} already exists`);

const base = args.name.replace(/-service$/, "");
const tokens = {
  __service__: args.name,
  __pkg__: base.replaceAll("-", ""),
  __Class__: base.split("-").map((p) => p[0].toUpperCase() + p.slice(1)).join(""),
  __port__: args.port,
  __route__: args.route,
  __db__: args.db,
  __dbuser__: args["db-user"],
  __description__: args.description || `${args.name} for SwiftDrop`,
};
const flags = { kafka: args.kafka, redis: args.redis, mongo: args.mongo };

function render(text) {
  // Section markers on their own line disappear together with their line break.
  let out = text.replace(/^[ \t]*(\{\{[#^/]\w+\}\})[ \t]*\r?\n/gm, "$1");
  out = out.replace(/\{\{#(\w+)\}\}([\s\S]*?)\{\{\/\1\}\}/g, (_, flag, body) => (flags[flag] ? body : ""));
  out = out.replace(/\{\{\^(\w+)\}\}([\s\S]*?)\{\{\/\1\}\}/g, (_, flag, body) => (flags[flag] ? "" : body));
  for (const [token, value] of Object.entries(tokens)) out = out.replaceAll(token, value);
  return out;
}

const created = [];
function copy(src, dest) {
  for (const entry of readdirSync(src)) {
    const from = join(src, entry);
    const to = join(dest, render(entry));
    if (statSync(from).isDirectory()) {
      copy(from, to);
    } else {
      mkdirSync(dirname(to), { recursive: true });
      writeFileSync(to, render(readFileSync(from, "utf8")));
      created.push(relative(root, to));
    }
  }
}
copy(templateDir, targetDir);

if (args.type === "java") {
  const pomPath = join(root, "pom.xml");
  const pom = readFileSync(pomPath, "utf8");
  const moduleLine = `<module>services/${args.name}</module>`;
  if (!pom.includes(moduleLine)) {
    writeFileSync(pomPath, pom.replace(/(\s*)<\/modules>/, `\n        ${moduleLine}$1</modules>`));
    console.log(`✔ registered ${moduleLine} in pom.xml`);
  }
}

console.log(`✔ created services/${args.name} (${created.length} files)`);
console.log(`
Next steps:
  1. Add a route in services/api-gateway/src/main/resources/application.yml for /api/v1/${args.route}/**
  2. Add the service to docker-compose.yml (profile "apps") and prometheus.yml
  3. Add a Helm values block in infra/helm/swiftdrop/values.yaml
  4. Record the owner in doc/03-team-contribution.md and .github/CODEOWNERS`);
