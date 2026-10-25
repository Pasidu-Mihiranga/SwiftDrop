import Link from "next/link";
import { auth, signOut } from "@/auth";
import { navigationFor } from "@/lib/roles";

export async function SiteHeader() {
  const session = await auth();
  const user = session && !session.error ? session.user : undefined;

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3 sm:px-6">
        <Link href="/" className="flex items-center gap-2 text-lg font-bold text-brand-700">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 text-white">S</span>
          SwiftDrop
        </Link>
        <nav className="flex flex-1 flex-wrap items-center gap-1 text-sm">
          <Link href="/track" className="rounded-md px-3 py-2 text-slate-600 hover:bg-slate-100">Track</Link>
          <Link href="/quote" className="rounded-md px-3 py-2 text-slate-600 hover:bg-slate-100">Get a price</Link>
          {user &&
            navigationFor(user.roles).map((item) => (
              <Link key={item.href} href={item.href} className="rounded-md px-3 py-2 text-slate-600 hover:bg-slate-100">
                {item.label}
              </Link>
            ))}
        </nav>
        {user ? (
          <form
            action={async () => {
              "use server";
              await signOut({ redirectTo: "/" });
            }}
            className="flex items-center gap-3"
          >
            <span className="hidden text-sm text-slate-600 sm:inline">{user.name ?? user.email}</span>
            <button className="btn-secondary" type="submit">Sign out</button>
          </form>
        ) : (
          <Link href="/login" className="btn-primary">Sign in</Link>
        )}
      </div>
    </header>
  );
}
