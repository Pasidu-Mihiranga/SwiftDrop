/** What happened during the demo, newest first, in memory (the simulator keeps no database). */
export class DemoLog {
  constructor(limit = 50) {
    this.limit = limit;
    this.orders = [];
    this.webhooks = [];
    this.failMode = false;
    this.webhookSecret = "";
  }

  addOrder(entry) {
    this.orders.unshift({ at: new Date().toISOString(), ...entry });
    this.orders.length = Math.min(this.orders.length, this.limit);
  }

  addWebhook(entry) {
    this.webhooks.unshift({ at: new Date().toISOString(), ...entry });
    this.webhooks.length = Math.min(this.webhooks.length, this.limit);
  }
}
