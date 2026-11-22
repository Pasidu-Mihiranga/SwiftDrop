import Link from "next/link";
import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";
import { fetchActiveZones } from "@/lib/pricing-api";
import {
  adminProblemMessage,
  filtersFrom,
  pageHref,
  ROLE_LABELS,
  searchQuery,
  type AdminUserPage,
} from "@/lib/admin-users";
import { UserAdmin } from "./user-admin";

export const metadata = { title: "Users" };

async function loadUsers(query: string): Promise<{ page: AdminUserPage } | { error: string }> {
  try {
    const response = await gatewayFetch(`/api/v1/admin/users?${query}`, { headers: { Accept: "application/json" } });
    if (response.ok) return { page: (await response.json()) as AdminUserPage };
    const body = (await response.json().catch(() => null)) as { code?: string } | null;
    return { error: adminProblemMessage(response.status, body?.code) };
  } catch {
    return { error: adminProblemMessage(503) };
  }
}

/** Admin user management (ADMIN only: proxy.ts rule "/admin/users", user-service checks again). */
export default async function AdminUsersPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const filters = filtersFrom(await searchParams);
  const [session, result, zones] = await Promise.all([auth(), loadUsers(searchQuery(filters)), fetchActiveZones()]);
  const zoneOptions = zones.zones.filter((zone) => zone.id).map((zone) => ({ id: zone.id as string, label: `${zone.name} (${zone.code})` }));

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Users</h1>
        <p className="mt-1 text-sm text-slate-600">
          Create courier, dispatcher and admin accounts, disable accounts and change staff roles. Courier vehicle, zone and
          capacity live on the <Link href="/admin/couriers" className="font-medium text-brand-700 hover:underline">Couriers</Link> page.
        </p>
      </header>

      <form method="get" className="card flex flex-wrap items-end gap-3" role="search" aria-label="Find accounts">
        <div className="min-w-48 flex-1 space-y-1.5">
          <label htmlFor="search" className="text-sm font-medium">Name or e-mail</label>
          <input id="search" name="search" defaultValue={filters.search} className="input" placeholder="e.g. kasun or @swiftdrop.lk" />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="role" className="text-sm font-medium">Role</label>
          <select id="role" name="role" defaultValue={filters.role} className="input">
            <option value="">Any role</option>
            {Object.entries(ROLE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5">
          <label htmlFor="status" className="text-sm font-medium">Status</label>
          <select id="status" name="status" defaultValue={filters.status} className="input">
            <option value="">Any status</option>
            <option value="active">Active</option>
            <option value="disabled">Disabled</option>
          </select>
        </div>
        <button type="submit" className="btn-secondary active:scale-[0.97] motion-reduce:active:scale-100">Search</button>
        {(filters.search || filters.role || filters.status) && (
          <Link href="/admin/users" className="rounded-md px-3 py-2.5 text-sm text-slate-600 hover:bg-slate-100">Clear</Link>
        )}
      </form>

      {"page" in result ? (
        <UserAdmin
          page={result.page}
          currentUserId={session?.user.id ?? ""}
          zones={zoneOptions}
          previousHref={result.page.page > 0 ? pageHref(filters, result.page.page - 1) : null}
          nextHref={(result.page.page + 1) * result.page.size < result.page.total ? pageHref(filters, result.page.page + 1) : null}
        />
      ) : (
        <div role="alert" className="card border-amber-200 bg-amber-50 text-sm text-amber-900">{result.error}</div>
      )}
    </div>
  );
}
