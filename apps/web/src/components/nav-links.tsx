"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export interface NavItem {
  href: string;
  label: string;
}

const BASE_LINK_CLASS = "rounded-md px-3 py-2 transition-colors";
const ACTIVE_LINK_CLASS = `${BASE_LINK_CLASS} bg-brand-50 text-brand-700 font-semibold`;
const INACTIVE_LINK_CLASS = `${BASE_LINK_CLASS} text-slate-600 hover:bg-slate-100 hover:text-slate-900`;

function matchesPath(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * Determines whether a nav link should be marked active for the current pathname.
 * When multiple links share a prefix (for example `/customer` and `/customer/history`, or
 * `/admin` and `/admin/notifications/sms-inbox`), only the longest matching prefix wins.
 */
export function isNavItemActive(pathname: string, href: string, allHrefs: readonly string[]): boolean {
  if (!pathname || !matchesPath(pathname, href)) return false;

  let longestMatch = href;
  for (const candidate of allHrefs) {
    if (matchesPath(pathname, candidate) && candidate.length > longestMatch.length) {
      longestMatch = candidate;
    }
  }
  return longestMatch === href;
}

/** Client-rendered navigation bar links that highlight whichever section matches the active route. */
export function NavLinks({ items }: { items: readonly NavItem[] }) {
  const pathname = usePathname() ?? "";
  const allHrefs = items.map((item) => item.href);

  return (
    <>
      {items.map((item) => {
        const active = isNavItemActive(pathname, item.href, allHrefs);
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={active ? ACTIVE_LINK_CLASS : INACTIVE_LINK_CLASS}
          >
            {item.label}
          </Link>
        );
      })}
    </>
  );
}

/** Standalone navigation link (such as "My profile") with active-route highlighting. */
export function NavLink({
  href,
  label,
  allHrefs,
  className = "",
}: {
  href: string;
  label: string;
  allHrefs?: readonly string[];
  className?: string;
}) {
  const pathname = usePathname() ?? "";
  const active = isNavItemActive(pathname, href, allHrefs ?? [href]);
  const stateClass = active ? ACTIVE_LINK_CLASS : INACTIVE_LINK_CLASS;

  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={className ? `${stateClass} ${className}` : stateClass}
    >
      {label}
    </Link>
  );
}
