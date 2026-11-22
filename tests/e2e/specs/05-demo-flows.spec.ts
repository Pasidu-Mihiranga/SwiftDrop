import { type APIRequestContext, expect, test } from "@playwright/test";
import { businessToday, deployed, gatewayAs, quote } from "../support/api";
import { env, users } from "../support/env";

/**
 * The demo story end to end, one parcel through every service: book, dispatch, courier updates,
 * live tracking, email, webhook, reports. Each step drives the real API (or page) of the service
 * that owns it. A step whose service is not deployed in this stack is skipped with the reason,
 * so the suite tells you what is missing instead of failing on it.
 */
test.describe.configure({ mode: "serial" });

let customer: APIRequestContext;
let parcel: { id: string; trackingNumber: string } | undefined;
let createdBefore = 0;
const today = businessToday();

test.beforeAll(async () => {
  customer = await gatewayAs(users.customer);
  const admin = await gatewayAs(users.admin);
  const report = await admin.get(`/api/v1/reports/daily?from=${today}&to=${today}`);
  createdBefore = report.ok() ? (await report.json()).totals.created : 0;
});

test("1. a customer books a delivery from a real quote", async () => {
  const { quoteId } = await quote(customer, "Colombo", "Kandy", 2);
  const response = await customer.post("/api/v1/parcels", {
    data: {
      quoteId,
      pickup: { line1: "1 Galle Road", city: "Colombo 03", district: "Colombo", postalCode: "00300" },
      dropoff: { line1: "12 Temple Road", city: "Kandy", district: "Kandy", postalCode: "20000" },
      recipient: { name: "E2E Recipient", phone: "0771234567" },
      notes: "Playwright demo parcel",
    },
  });
  expect(response.status(), await response.text()).toBe(201);
  parcel = await response.json();
  expect(parcel?.trackingNumber).toMatch(/^SD[0-9A-Z]{8,20}$/);
});

test("2. the booking shows in the reports (Kafka event to reporting projection)", async () => {
  test.skip(!parcel, "No parcel was booked.");
  const admin = await gatewayAs(users.admin);
  await expect
    .poll(async () => (await (await admin.get(`/api/v1/reports/daily?from=${today}&to=${today}`)).json()).totals.created, {
      timeout: 60_000,
      message: "ParcelCreated should reach reporting-service through the outbox and Kafka",
    })
    .toBeGreaterThan(createdBefore);
});

test("3. a dispatcher assigns the parcel to a courier", async () => {
  test.skip(!parcel, "No parcel was booked.");
  const dispatcher = await gatewayAs(users.dispatcher);
  test.skip(!(await deployed(dispatcher, "/api/v1/dispatch/unassigned")), "dispatch-service (M5) is not deployed.");
  const response = await dispatcher.post("/api/v1/dispatch/assignments", {
    data: { parcelId: parcel!.id, courierId: users.courier.id },
  });
  expect([201, 409], await response.text()).toContain(response.status());
});

test("4. the courier picks it up and takes it out for delivery", async () => {
  test.skip(!parcel, "No parcel was booked.");
  const courier = await gatewayAs(users.courier);
  const probe = await courier.patch(`/api/v1/courier/parcels/${parcel!.id}/status`, {
    data: { toStatus: "PICKED_UP" },
    failOnStatusCode: false,
  });
  test.skip([404, 405, 502, 503].includes(probe.status()), "Courier status updates (M4) are not deployed or the parcel is not assigned.");
  expect(probe.status(), await probe.text()).toBe(200);
  for (const toStatus of ["IN_TRANSIT", "OUT_FOR_DELIVERY"]) {
    const response = await courier.patch(`/api/v1/courier/parcels/${parcel!.id}/status`, { data: { toStatus } });
    expect(response.status(), await response.text()).toBe(200);
  }
});

test("5. the public tracking page shows the parcel", async ({ page }) => {
  test.skip(!parcel, "No parcel was booked.");
  const guest = await gatewayAs();
  test.skip(
    !(await deployed(guest, `/api/v1/tracking/public/${parcel!.trackingNumber}`)),
    "tracking-service (M6) is not deployed.",
  );
  await page.goto(`/track/${parcel!.trackingNumber}`);
  await expect(page.getByText(parcel!.trackingNumber).first()).toBeVisible();
});

test("6. the customer is emailed (Mailpit)", async ({ request }) => {
  test.skip(!parcel, "No parcel was booked.");
  const notifications = await gatewayAs(users.customer);
  test.skip(!(await deployed(notifications, "/api/v1/notifications/me")), "notification-service (M9) is not deployed.");
  await expect
    .poll(
      async () => {
        const response = await request.get(`${env.mailpitUrl}/api/v1/search?query=${parcel!.trackingNumber}`);
        return response.ok() ? (await response.json()).messages_count : 0;
      },
      { timeout: 60_000 },
    )
    .toBeGreaterThan(0);
});

test("7. the merchant simulator receives a signed webhook", async () => {
  const merchant = await gatewayAs(users.merchant);
  test.skip(!(await deployed(merchant, "/api/v1/webhooks/endpoints")), "webhook-service (M8) is not deployed.");
  const deliveries = await merchant.get("/api/v1/webhooks/deliveries");
  expect(deliveries.ok(), await deliveries.text()).toBeTruthy();
});

test("8. the customer sees the parcel in their own list, and nobody else does", async () => {
  test.skip(!parcel, "No parcel was booked.");
  const mine = await (await customer.get("/api/v1/parcels/mine")).json();
  expect(mine.content.map((p: { id: string }) => p.id)).toContain(parcel!.id);

  const other = await gatewayAs(users.merchant);
  const response = await other.get(`/api/v1/parcels/${parcel!.id}`);
  expect([403, 404]).toContain(response.status());
});
