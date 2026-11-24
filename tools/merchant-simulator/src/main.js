import { createServer } from "node:http";
import { createHandler } from "./app.js";
import { loadConfig } from "./config.js";
import { MerchantApi } from "./merchant-api.js";
import { DemoLog } from "./store.js";

const config = loadConfig();
const handler = createHandler({ config, api: new MerchantApi(config), log: new DemoLog() });

createServer((request, response) => void handler(request, response)).listen(config.port, () => {
  console.log(`merchant-simulator listening on :${config.port} as ${config.clientId}; webhooks at ${config.publicWebhookUrl}`);
});
