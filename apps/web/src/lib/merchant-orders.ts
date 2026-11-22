/**
 * The merchant orders dashboard (M7): the order shapes parcel-service returns, the filters read
 * from the address bar and the CSV export. Nothing here talks to the network, so it is safe on the
 * server and in the browser.
 *
 * Schemas: contracts/openapi/parcel-service.v1.yaml (MerchantOrder, MerchantOrderPage)
 */

export const PARCEL_STATUSES = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "FAILED",
  "CANCELLED",
] as const;
export type ParcelStatus = (typeof PARCEL_STATUSES)[number];

interface Address {
  line1: string;
  line2?: string;
  city: string;
  district: string;
  postalCode?: string;
}

export interface MerchantOrder {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  merchantOrderRef: string;
  pickup: Address;
  dropoff: Address;
  recipient: { name: string; phone: string };
  weightKg: number;
  sizeClass: string;
  serviceLevel?: string;
  fee: { amount: number; currency: string };
  createdAt: string;
}

export interface MerchantOrderPage {
  content: MerchantOrder[];
  page: { page: number; size: number; totalElements: number; totalPages: number };
}

/** Rows per page on the dashboard. */
export const ORDERS_PAGE_SIZE = 20;
/** The largest page parcel-service serves, used when exporting. */
export const EXPORT_PAGE_SIZE = 100;
/** An export stops here rather than reading without end; the file says so when it was cut short. */
export const EXPORT_MAX_ROWS = 10_000;

/** The filters as the merchant chose them: dates are calendar days in Sri Lanka (YYYY-MM-DD). */
export interface OrderFilter {
  status?: ParcelStatus;
  from?: string;
  to?: string;
}

export type OrdersQuery = { filter: OrderFilter; page: number; error?: string };

const STATUS_LABELS: Record<ParcelStatus, string> = {
  CREATED: "Created",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Delivery failed",
  CANCELLED: "Cancelled",
};

export function parcelStatusLabel(status: ParcelStatus): string {
  return STATUS_LABELS[status] ?? status;
}

/** One of the design-system pill classes from globals.css, so no colours are repeated here. */
export function parcelStatusPillClass(status: ParcelStatus): string {
  if (status === "DELIVERED") return "status-pill-success";
  if (status === "FAILED") return "status-pill-warning";
  return "status-pill-neutral";
}

export function isParcelStatus(value: unknown): value is ParcelStatus {
  return typeof value === "string" && (PARCEL_STATUSES as readonly string[]).includes(value);
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;

function isCalendarDay(value: string | undefined): value is string {
  if (!value || !DAY.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value);
}

/**
 * Reads the filters and page from the address bar. Anything unexpected is dropped rather than
 * failing the page; a "from" day after the "to" day is reported so the merchant can fix it.
 */
export function parseOrdersQuery(params: { status?: string; from?: string; to?: string; page?: string }): OrdersQuery {
  const filter: OrderFilter = {};
  if (isParcelStatus(params.status)) filter.status = params.status;
  if (isCalendarDay(params.from)) filter.from = params.from;
  if (isCalendarDay(params.to)) filter.to = params.to;

  const page = Number.parseInt(params.page ?? "", 10);
  const query: OrdersQuery = { filter, page: Number.isInteger(page) && page >= 0 ? page : 0 };
  if (filter.from && filter.to && filter.from > filter.to) {
    query.error = "The 'from' date must be on or before the 'to' date.";
  }
  return query;
}

/** Sri Lanka has one fixed offset and no daylight saving. */
const COLOMBO_OFFSET = "+05:30";

function nextDay(day: string): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

/**
 * The parcel-service query for a page of orders. The days become instants at midnight in Colombo;
 * "to" includes the whole day, because the service's `to` is exclusive.
 */
export function ordersApiQuery(filter: OrderFilter, page: number, size: number): URLSearchParams {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filter.status) query.set("status", filter.status);
  if (filter.from) query.set("from", new Date(`${filter.from}T00:00:00${COLOMBO_OFFSET}`).toISOString());
  if (filter.to) query.set("to", new Date(`${nextDay(filter.to)}T00:00:00${COLOMBO_OFFSET}`).toISOString());
  return query;
}

/** The filters as they appear in the dashboard's own links (export, pages). */
export function filterSearch(filter: OrderFilter, page?: number): string {
  const query = new URLSearchParams();
  if (filter.status) query.set("status", filter.status);
  if (filter.from) query.set("from", filter.from);
  if (filter.to) query.set("to", filter.to);
  if (page && page > 0) query.set("page", String(page));
  const text = query.toString();
  return text ? `?${text}` : "";
}

export function formatFee(fee: { amount: number; currency: string }): string {
  return `${fee.currency} ${Number(fee.amount).toFixed(2)}`;
}

const CSV_COLUMNS: [string, (order: MerchantOrder) => string][] = [
  ["Tracking number", (o) => o.trackingNumber],
  ["Order reference", (o) => o.merchantOrderRef],
  ["Status", (o) => o.status],
  ["Created (UTC)", (o) => o.createdAt],
  ["Recipient", (o) => o.recipient.name],
  ["Recipient phone", (o) => o.recipient.phone],
  ["Pickup city", (o) => o.pickup.city],
  ["Pickup district", (o) => o.pickup.district],
  ["Drop-off address", (o) => [o.dropoff.line1, o.dropoff.line2].filter(Boolean).join(", ")],
  ["Drop-off city", (o) => o.dropoff.city],
  ["Drop-off district", (o) => o.dropoff.district],
  ["Weight (kg)", (o) => String(o.weightKg)],
  ["Size", (o) => o.sizeClass],
  ["Service", (o) => o.serviceLevel ?? ""],
  ["Fee", (o) => Number(o.fee.amount).toFixed(2)],
  ["Currency", (o) => o.fee.currency],
];

/**
 * One CSV cell. Quotes are doubled and the cell is quoted when it holds a separator, quote or line
 * break. A cell a spreadsheet would read as a formula (=, +, -, @, tab, carriage return) gets a
 * leading apostrophe, so text a customer typed can never run as a formula in the merchant's sheet.
 */
export function csvCell(value: string): string {
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return /[",\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

/** The orders as CSV (RFC 4180, CRLF line ends), with a header row. */
export function ordersToCsv(orders: MerchantOrder[]): string {
  const header = CSV_COLUMNS.map(([name]) => csvCell(name)).join(",");
  const rows = orders.map((order) => CSV_COLUMNS.map(([, read]) => csvCell(read(order))).join(","));
  return [header, ...rows].join("\r\n") + "\r\n";
}

/** e.g. swiftdrop-orders-2026-11-22.csv, dated in Colombo. */
export function exportFileName(now: Date = new Date()): string {
  const day = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Colombo" }).format(now);
  return `swiftdrop-orders-${day}.csv`;
}
