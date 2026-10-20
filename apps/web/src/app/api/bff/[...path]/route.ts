import type { NextRequest } from "next/server";
import { accessToken } from "@/lib/gateway";
import { gatewayUrl } from "@/lib/keycloak";

/**
 * Backend-for-frontend proxy: browser code calls /api/bff/api/v1/... and this handler forwards the
 * request to the gateway with the user's access token, so tokens never reach browser JavaScript.
 */
const FORWARDED_REQUEST_HEADERS = ["content-type", "accept", "x-correlation-id", "idempotency-key"];
const FORWARDED_RESPONSE_HEADERS = ["content-type", "x-correlation-id", "content-disposition", "www-authenticate"];

async function proxy(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }): Promise<Response> {
  const { path } = await params;
  const target = path.join("/");
  if (!target.startsWith("api/v1/")) {
    return Response.json({ status: 404, code: "NOT_FOUND", detail: "Unknown API path" }, { status: 404 });
  }

  const outgoing = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) outgoing.set(name, value);
  }
  const token = await accessToken(request.headers);
  if (token) outgoing.set("authorization", `Bearer ${token}`);

  const hasBody = !["GET", "HEAD"].includes(request.method);
  const upstream = await fetch(`${gatewayUrl()}/${target}${request.nextUrl.search}`, {
    method: request.method,
    headers: outgoing,
    body: hasBody ? await request.arrayBuffer() : undefined,
    cache: "no-store",
    redirect: "manual",
  });

  const responseHeaders = new Headers();
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

export { proxy as DELETE, proxy as GET, proxy as PATCH, proxy as POST, proxy as PUT };
