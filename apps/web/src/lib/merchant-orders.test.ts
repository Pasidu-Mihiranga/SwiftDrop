import { describe, expect, it } from "vitest";
import {
  ORDER_REF_MAX_LENGTH,
  csvCell,
  exportFileName,
  filterSearch,
  formatFee,
  isParcelStatus,
  ordersApiQuery,
  ordersToCsv,
  parcelStatusLabel,
  parcelStatusPillClass,
  parseOrdersQuery,
  type MerchantOrder,
} from "@/lib/merchant-orders";

const ORDER: MerchantOrder = {
  id: "7e726c28-4a72-4fea-9ec1-c56b08262ddc",
  trackingNumber: "SD261121THPJ1AY",
  status: "CREATED",
  merchantOrderRef: "WEB-1001",
  pickup: { line1: "12 Galle Road", city: "Colombo 03", district: "Colombo" },
  dropoff: { line1: "45 Peradeniya Road", line2: "Flat 2", city: "Kandy", district: "Kandy" },
  recipient: { name: "Saman Perera", phone: "0712345678" },
  weightKg: 2,
  sizeClass: "S",
  serviceLevel: "STANDARD",
  fee: { amount: 631, currency: "LKR" },
  createdAt: "2026-11-21T15:19:50Z",
};

describe("parseOrdersQuery", () => {
  it("reads a status, a date range and a page", () => {
    expect(parseOrdersQuery({ status: "DELIVERED", from: "2026-11-01", to: "2026-11-30", page: "2" })).toEqual({
      filter: { status: "DELIVERED", from: "2026-11-01", to: "2026-11-30" },
      page: 2,
    });
  });

  it("drops anything it does not recognise instead of failing the page", () => {
    expect(parseOrdersQuery({ status: "LOST", from: "yesterday", to: "2026-02-30", page: "-1" })).toEqual({
      filter: {},
      page: 0,
    });
  });

  it("reports a from day after the to day", () => {
    const query = parseOrdersQuery({ from: "2026-11-30", to: "2026-11-01" });
    expect(query.error).toMatch(/on or before/);
  });

  it("accepts a single-day range", () => {
    expect(parseOrdersQuery({ from: "2026-11-21", to: "2026-11-21" }).error).toBeUndefined();
  });

  it("reads an order-reference search, trimmed, alongside the other filters", () => {
    expect(parseOrdersQuery({ orderRef: "  WEB-1001 ", status: "CREATED", page: "1" })).toEqual({
      filter: { status: "CREATED", orderRef: "WEB-1001" },
      page: 1,
    });
  });

  it("treats a blank search as no search", () => {
    expect(parseOrdersQuery({ orderRef: "   " }).filter).toEqual({});
  });

  it("reports a search longer than any stored reference", () => {
    expect(parseOrdersQuery({ orderRef: "x".repeat(ORDER_REF_MAX_LENGTH) }).error).toBeUndefined();
    expect(parseOrdersQuery({ orderRef: "x".repeat(ORDER_REF_MAX_LENGTH + 1) }).error).toMatch(/at most 100/);
  });
});

describe("ordersApiQuery", () => {
  it("turns Sri Lankan calendar days into instants, with the to day included in full", () => {
    const query = ordersApiQuery({ status: "CREATED", from: "2026-11-01", to: "2026-11-30" }, 1, 20);

    expect(query.get("status")).toBe("CREATED");
    // Midnight in Colombo (UTC+05:30) is 18:30 UTC the day before.
    expect(query.get("from")).toBe("2026-10-31T18:30:00.000Z");
    // parcel-service's "to" is exclusive, so it is midnight at the start of the next day.
    expect(query.get("to")).toBe("2026-11-30T18:30:00.000Z");
    expect(query.get("page")).toBe("1");
    expect(query.get("size")).toBe("20");
  });

  it("crosses a month and a year end", () => {
    expect(ordersApiQuery({ to: "2026-12-31" }, 0, 20).get("to")).toBe("2026-12-31T18:30:00.000Z");
  });

  it("sends no filter the merchant did not choose", () => {
    expect(ordersApiQuery({}, 0, 100).toString()).toBe("page=0&size=100");
  });

  it("sends the order-reference search as typed, encoded for the address", () => {
    const query = ordersApiQuery({ orderRef: "SALE 50%&more", status: "CREATED" }, 0, 20);

    expect(query.get("orderRef")).toBe("SALE 50%&more");
    expect(query.toString()).toBe("page=0&size=20&status=CREATED&orderRef=SALE+50%25%26more");
  });
});

describe("filterSearch", () => {
  it("keeps the filters in the dashboard's own links", () => {
    expect(filterSearch({ status: "DELIVERED", from: "2026-11-01" }, 3)).toBe("?status=DELIVERED&from=2026-11-01&page=3");
  });

  it("keeps the search in the page and export links, so paging stays within the results", () => {
    expect(filterSearch({ orderRef: "WEB-10", status: "CREATED" }, 2)).toBe("?status=CREATED&orderRef=WEB-10&page=2");
  });

  it("is empty with no filters on the first page", () => {
    expect(filterSearch({}, 0)).toBe("");
  });
});

describe("csvCell", () => {
  it("leaves plain text alone", () => {
    expect(csvCell("Saman Perera")).toBe("Saman Perera");
  });

  it("quotes a cell holding a comma, a quote or a line break", () => {
    expect(csvCell("Flat 2, Kandy")).toBe('"Flat 2, Kandy"');
    expect(csvCell('The "Book" Shop')).toBe('"The ""Book"" Shop"');
    expect(csvCell("line one\nline two")).toBe('"line one\nline two"');
  });

  it("stops a spreadsheet from running text as a formula", () => {
    expect(csvCell("=HYPERLINK(\"http://x\")")).toBe("\"'=HYPERLINK(\"\"http://x\"\")\"");
    expect(csvCell("+94712345678")).toBe("'+94712345678");
    expect(csvCell("-1")).toBe("'-1");
    expect(csvCell("@SUM(A1)")).toBe("'@SUM(A1)");
  });
});

describe("ordersToCsv", () => {
  it("writes a header and one row per order with CRLF line ends", () => {
    const csv = ordersToCsv([ORDER]);
    const [header, row, end] = csv.split("\r\n");

    expect(header).toMatch(/^Tracking number,Order reference,Status,Created \(UTC\),Recipient/);
    expect(row).toBe(
      "SD261121THPJ1AY,WEB-1001,CREATED,2026-11-21T15:19:50Z,Saman Perera,0712345678,Colombo 03,Colombo," +
        '"45 Peradeniya Road, Flat 2",Kandy,Kandy,2,S,STANDARD,631.00,LKR',
    );
    expect(end).toBe("");
  });

  it("writes only the header when there are no orders", () => {
    expect(ordersToCsv([]).split("\r\n")).toHaveLength(2);
  });

  it("leaves the service level empty when the order has none", () => {
    const { serviceLevel: _omitted, ...withoutService } = ORDER;
    expect(ordersToCsv([withoutService]).split("\r\n")[1]).toContain(",S,,631.00,");
  });
});

describe("labels and formatting", () => {
  it("names each status and picks a pill from the design system", () => {
    expect(parcelStatusLabel("OUT_FOR_DELIVERY")).toBe("Out for delivery");
    expect(parcelStatusPillClass("DELIVERED")).toBe("status-pill-success");
    expect(parcelStatusPillClass("FAILED")).toBe("status-pill-warning");
    expect(parcelStatusPillClass("IN_TRANSIT")).toBe("status-pill-neutral");
    expect(isParcelStatus("IN_TRANSIT")).toBe(true);
    expect(isParcelStatus(42)).toBe(false);
  });

  it("shows a fee with two decimals", () => {
    expect(formatFee({ amount: 631, currency: "LKR" })).toBe("LKR 631.00");
  });

  it("dates the export file in Colombo", () => {
    // 20:00 UTC on 21 November is already 22 November in Sri Lanka.
    expect(exportFileName(new Date("2026-11-21T20:00:00Z"))).toBe("swiftdrop-orders-2026-11-22.csv");
  });
});
