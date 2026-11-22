import Link from "next/link";
import { redirect } from "next/navigation";
import { auth, signOut } from "@/auth";
import { idToken } from "@/lib/gateway";
import { appUrl, keycloakConfig, keycloakLogoutUrl } from "@/lib/keycloak";
import { navigationFor } from "@/lib/roles";
import { NavLink, NavLinks } from "@/components/nav-links";
import { NotificationBell } from "@/components/notification-bell";

export async function SiteHeader() {
  const session = await auth();
  const user = session && !session.error ? session.user : undefined;
  const navItems = [
    { href: "/track", label: "Track" },
    { href: "/quote", label: "Get a price" },
    ...(user ? navigationFor(user.roles) : []),
  ];

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3 sm:px-6">
        <Link href="/" className="flex items-center gap-2 text-lg font-bold text-brand-700">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-white">S</span>
          SwiftDrop
        </Link>
        <nav aria-label="Primary" className="flex flex-1 flex-wrap items-center gap-1 text-sm">
          <NavLinks items={navItems} />
        </nav>
        {user ? (
          <div className="flex items-center gap-3">
            <NotificationBell />
            <form
              action={async () => {
                "use server";
                // End both sessions: the app cookie here, then Keycloak's SSO session, which would
                // otherwise sign the same user straight back in on the next "Sign in".
                const hint = await idToken();
                await signOut({ redirect: false });
                redirect(keycloakLogoutUrl(keycloakConfig(), `${appUrl()}/`, hint));
              }}
              className="flex items-center gap-3"
            >
              <span className="hidden text-sm text-slate-600 sm:inline">{user.name ?? user.email}</span>
              <NavLink href="/account" label="My profile" className="text-sm" />
              <button className="btn-secondary" type="submit">Sign out</button>
            </form>
          </div>
        ) : (
          <Link href="/login" className="btn-primary">Sign in</Link>
        )}
      </div>
    </header>
  );
}
