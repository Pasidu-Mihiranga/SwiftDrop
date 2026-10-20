import { NextResponse } from "next/server";
import { auth } from "@/auth";
import { accessFor } from "@/lib/roles";

/**
 * Next.js proxy (formerly "middleware"): role-based page protection. Runs before every page request;
 * reading the session here also refreshes Keycloak tokens shortly before they expire.
 */
export default auth((request) => {
  const { pathname, search } = request.nextUrl;
  const session = request.auth;
  const roles = session && !session.error ? session.user.roles : undefined;

  switch (accessFor(pathname, roles)) {
    case "login-required": {
      const login = new URL("/login", request.nextUrl.origin);
      login.searchParams.set("callbackUrl", `${pathname}${search}`);
      return NextResponse.redirect(login);
    }
    case "forbidden":
      return NextResponse.redirect(new URL("/forbidden", request.nextUrl.origin));
    default:
      return NextResponse.next();
  }
});

export const config = {
  // Pages only: skip Next.js assets, Auth.js endpoints and the BFF (which forwards the gateway's 401/403).
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/).*)"],
};
