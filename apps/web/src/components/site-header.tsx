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
  // Visitors get the marketing links; signed-in people get the links their roles open.
  const navItems = user
    ? [{ href: "/track", label: "Track" }, { href: "/quote", label: "Get a price" }, ...navigationFor(user.roles)]
    : [
        { href: "/track", label: "Track a parcel" },
        { href: "/quote", label: "Prices" },
        { href: "/#stores", label: "For online stores" },
        { href: "/#help", label: "Help" },
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
    <header className={`relative bg-slate-50 ${user ? "border-b border-slate-200" : ""}`}>
      <div className="mx-auto flex min-h-[72px] max-w-[1248px] items-center gap-4 px-6 py-3 md:min-h-[84px] md:flex-wrap">
        <Link href="/" aria-label="SwiftDrop home" className="shrink-0 rounded-md">
          {/* eslint-disable-next-line @next/next/no-img-element -- a fixed-size AVIF mark needs no optimisation */}
          <img src="/brand/swiftdrop-logo-light.avif" alt="SwiftDrop" width={640} height={322} className="h-10 w-auto md:h-12" />
        </Link>
        <nav
          aria-label="Primary"
          className={`hidden flex-1 flex-wrap items-center gap-1 text-[15px] font-medium md:flex ${user ? "" : "justify-center"}`}
        >
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
            <>
              <Link href="/login" className="hidden rounded-md px-2 py-2 text-[15px] font-medium text-slate-900 hover:text-brand-700 md:inline">
                Sign in
              </Link>
              <Link href="/login?callbackUrl=%2Fcustomer%2Fbook" className="btn-primary hidden sm:inline-flex">
                Send a parcel
              </Link>
            </>
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
            ) : (
              <div className="flex flex-wrap gap-2">
                <Link href="/login?callbackUrl=%2Fcustomer%2Fbook" className="btn-primary">Send a parcel</Link>
                <Link href="/login" className="btn-secondary">Sign in</Link>
              </div>
            )}
          </MobileNav>
        </div>
      </div>
    </header>
  );
}
