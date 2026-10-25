import Link from "next/link";
import type { ReactNode } from "react";
import { auth } from "@/auth";
import { adminNavigationFor } from "@/lib/roles";

/**
 * Operations shell. The sub-navigation only lists what the signed-in roles may open, so a
 * dispatcher never sees a pricing link they would be bounced out of.
 */
export default async function AdminLayout({ children }: { children: ReactNode }) {
  const session = await auth();
  const roles = session && !session.error ? session.user.roles : [];
  const links = adminNavigationFor(roles);

  return (
    <div className="space-y-6">
      {links.length > 1 ? (
        <nav aria-label="Operations" className="flex flex-wrap gap-1 border-b border-slate-200 pb-2 text-sm">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="rounded-md px-3 py-2 text-slate-600 hover:bg-slate-100"
            >
              {link.label}
            </Link>
          ))}
        </nav>
      ) : null}
      {children}
    </div>
  );
}
