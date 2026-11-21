import type { NextRequest } from "next/server";
import { auth } from "@/auth";
import { fetchAllMyOrders } from "@/lib/merchant-api";
import { exportFileName, ordersToCsv, parseOrdersQuery } from "@/lib/merchant-orders";

/**
 * The orders dashboard's CSV export: the same filters as the page, every matching order rather
 * than one page. The proxy already limits /merchant to MERCHANT users and parcel-service only
 * returns the caller's own merchant's orders; the session is checked here as well so this answers
 * plainly instead of redirecting a download to the login page.
 */
export async function GET(request: NextRequest): Promise<Response> {
  const session = await auth();
  if (!session || session.error || !session.user.roles.includes("MERCHANT")) {
    return problem(403, "FORBIDDEN", "Sign in as a merchant to export orders.");
  }

  const params = Object.fromEntries(request.nextUrl.searchParams);
  const { filter, error } = parseOrdersQuery(params);
  if (error) {
    return problem(400, "INVALID_DATE_RANGE", error);
  }

  let result: Awaited<ReturnType<typeof fetchAllMyOrders>>;
  try {
    result = await fetchAllMyOrders(filter);
  } catch {
    return problem(502, "ORDERS_UNAVAILABLE", "Your orders could not be loaded. Please try again shortly.");
  }

  // A byte-order mark so Excel reads the file as UTF-8 (names may be in Sinhala or Tamil).
  const body = "﻿" + ordersToCsv(result.orders);
  return new Response(body, {
    headers: {
      "content-type": "text/csv; charset=utf-8",
      "content-disposition": `attachment; filename="${exportFileName()}"`,
      "cache-control": "no-store",
      // Lets the merchant see when the export stopped at the row limit.
      "x-export-truncated": String(result.truncated),
    },
  });
}

function problem(status: number, code: string, detail: string): Response {
  return Response.json({ type: "about:blank", status, code, detail }, {
    status,
    headers: { "content-type": "application/problem+json", "cache-control": "no-store" },
  });
}
