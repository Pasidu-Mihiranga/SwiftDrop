import Link from "next/link";
import { redirect } from "next/navigation";
import { auth, signOut } from "@/auth";
import { idToken } from "@/lib/gateway";
import { appUrl, keycloakConfig, keycloakLogoutUrl } from "@/lib/keycloak";
import { navigationFor } from "@/lib/roles";
import { MobileNav } from "@/components/mobile-nav";
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

  async function signOutEverywhere() {
    "use server";
    // End both sessions: the app cookie here, then Keycloak's SSO session, which would
    // otherwise sign the same user straight back in on the next "Sign in".
    const hint = await idToken();
    await signOut({ redirect: false });
    redirect(keycloakLogoutUrl(keycloakConfig(), `${appUrl()}/`, hint));
  }

  return (
    <header className="relative border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3 sm:px-6 md:flex-wrap">
        <Link href="/" className="flex items-center gap-2 text-lg font-bold text-brand-700">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-white">S</span>
          SwiftDrop
        </Link>
        <nav aria-label="Primary" className="hidden flex-1 flex-wrap items-center gap-1 text-sm md:flex">
          <NavLinks items={navItems} />
        </nav>
        <div className="ml-auto flex items-center gap-2 md:ml-0 md:gap-3">
          {user ? (
            <>
              <NotificationBell />
              <form action={signOutEverywhere} className="hidden items-center gap-3 md:flex">
                <span className="hidden text-sm text-slate-600 lg:inline">{user.name ?? user.email}</span>
                <NavLink href="/account" label="My profile" className="text-sm" />
                <button className="btn-secondary" type="submit">Sign out</button>
              </form>
            </>
          ) : (
            <Link href="/login" className="btn-primary">Sign in</Link>
          )}
          <MobileNav items={navItems}>
            {user ? (
              <form action={signOutEverywhere} className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm text-slate-600">{user.name ?? user.email}</span>
                <span className="flex items-center gap-2">
                  <NavLink href="/account" label="My profile" className="text-sm" />
                  <button className="btn-secondary" type="submit">Sign out</button>
                </span>
              </form>
            ) : null}
          </MobileNav>
        </div>
      </div>
    </header>
  );
}
