// Prints an HTML file to a US Letter PDF with the page number at the bottom right, as in the team's
// design report. Usage: node printkit.mjs <in.html> <out.pdf>
import { readdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// playwright-core is installed by the workspace (tests/e2e); resolve it from the pnpm store.
const repo = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const store = join(repo, "node_modules", ".pnpm");
const pkg = readdirSync(store).filter((d) => d.startsWith("playwright-core@")).sort().pop();
if (!pkg) throw new Error("playwright-core not found; run pnpm install first");
const require = createRequire(join(store, pkg, "node_modules", "playwright-core", "package.json"));
const { chromium } = require("playwright-core");
const [src, out] = process.argv.slice(2);
const defaultChrome =
  process.platform === "win32"
    ? "C:/Program Files/Google/Chrome/Application/chrome.exe"
    : process.platform === "darwin"
      ? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
      : "/usr/bin/google-chrome";
const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || defaultChrome });
const page = await browser.newPage();
await page.goto(pathToFileURL(src).href, { waitUntil: "networkidle" });
await page.evaluate(() => document.fonts.ready);
await page.pdf({
  path: out,
  format: "Letter",
  printBackground: true,
  margin: { top: "0.9in", bottom: "0.9in", left: "1in", right: "1in" },
  displayHeaderFooter: true,
  headerTemplate: "<div></div>",
  footerTemplate:
    '<div style="width:100%;font-family:\'Times New Roman\',serif;font-size:10px;font-weight:bold;text-align:right;padding-right:1in;"><span class="pageNumber"></span></div>',
});
await browser.close();
console.log("pdf", out);
