/**
 * Role-based routing for the web app. The services enforce the real rules; this decides which
 * pages a user may open and where they land after signing in.
 */
export const ROLES = ["CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"] as const;
export type Role = (typeof ROLES)[number];

interface AreaRule {
  prefix: string;
  roles: Role[] | "any-authenticated";
}

/** Most specific prefix first: the first match wins. */
const AREAS: AreaRule[] = [
  { prefix: "/merchant/register", roles: "any-authenticated" },
  // Pricing configuration is administrator-only, which is narrower than the rest of /admin.
  // pricing-service enforces the same rule, so this only keeps the page out of the way.
  { prefix: "/admin/pricing", roles: ["ADMIN"] },
  { prefix: "/customer", roles: ["CUSTOMER"] },
  { prefix: "/merchant", roles: ["MERCHANT"] },
  { prefix: "/courier", roles: ["COURIER"] },
  { prefix: "/admin", roles: ["DISPATCHER", "ADMIN"] },
  { prefix: "/dashboard", roles: "any-authenticated" },
  { prefix: "/account", roles: "any-authenticated" },
];

const PUBLIC_PREFIXES = ["/", "/login", "/track", "/quote", "/forbidden"];

export type Access = "public" | "allowed" | "login-required" | "forbidden";

function matches(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export function accessFor(pathname: string, roles: readonly string[] | undefined): Access {
  const area = AREAS.find((rule) => matches(pathname, rule.prefix));
  if (!area) {
    const isPublic = PUBLIC_PREFIXES.some((prefix) => (prefix === "/" ? pathname === "/" : matches(pathname, prefix)));
    // Unknown pages are treated as private by default (secure by default).
    if (isPublic) return "public";
    return roles ? "allowed" : "login-required";
  }
  if (!roles) return "login-required";
  if (area.roles === "any-authenticated") return "allowed";
  return area.roles.some((role) => roles.includes(role)) ? "allowed" : "forbidden";
}

/** Landing page after sign-in, by most privileged role. */
export function homeFor(roles: readonly string[]): string {
  if (roles.includes("ADMIN")) return "/admin";
  if (roles.includes("DISPATCHER")) return "/admin/dispatch";
  if (roles.includes("COURIER")) return "/courier";
  if (roles.includes("MERCHANT")) return "/merchant";
  return "/customer";
}

/** Navigation entries shown in the header for a user's roles. */
export function navigationFor(roles: readonly string[]): { href: string; label: string }[] {
  const items: { href: string; label: string }[] = [];
  if (roles.includes("CUSTOMER")) items.push({ href: "/customer", label: "My deliveries" });
  if (roles.includes("MERCHANT")) items.push({ href: "/merchant", label: "Merchant portal" });
  if (roles.includes("COURIER")) items.push({ href: "/courier", label: "Today's runs" });
  if (roles.includes("DISPATCHER") || roles.includes("ADMIN")) items.push({ href: "/admin", label: "Operations" });
  return items;
}

/** Links inside the operations area, filtered to what the signed-in roles may open. */
export function adminNavigationFor(roles: readonly string[]): { href: string; label: string }[] {
  const items: { href: string; label: string }[] = [];
  if (roles.includes("DISPATCHER") || roles.includes("ADMIN")) {
    items.push({ href: "/admin", label: "Overview" });
    items.push({ href: "/admin/dispatch", label: "Dispatch" });
  }
  if (roles.includes("ADMIN")) {
    items.push({ href: "/admin/pricing/zones", label: "Zones & districts" });
    items.push({ href: "/admin/pricing/rate-cards", label: "Rate cards" });
  }
  return items;
}

/** Realm roles from a Keycloak access token payload (the token comes straight from Keycloak's token endpoint). */
export function rolesFromAccessToken(accessToken: string): Role[] {
  try {
    const payload = JSON.parse(Buffer.from(accessToken.split(".")[1] ?? "", "base64url").toString("utf8")) as {
      realm_access?: { roles?: string[] };
    };
    return (payload.realm_access?.roles ?? []).filter((r): r is Role => (ROLES as readonly string[]).includes(r));
  } catch {
    return [];
  }
}
