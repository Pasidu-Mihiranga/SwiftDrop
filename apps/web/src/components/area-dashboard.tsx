import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";

interface Props {
  title: string;
  subtitle: string;
  /** Features this area will get, with the owning team member (see doc/03-team-contribution.md). */
  upcoming: { feature: string; owner: string }[];
  /** Service whose /meta/whoami proves the browser → BFF → gateway → service JWT chain. */
  check: { service: string; path: string };
}

async function platformCheck(path: string): Promise<{ ok: boolean; detail: string }> {
  try {
    const res = await gatewayFetch(path);
    if (res.status >= 500) return { ok: false, detail: `service not reachable (HTTP ${res.status}) — is it running?` };
    if (!res.ok) return { ok: false, detail: `HTTP ${res.status}` };
    const body = (await res.json()) as { service: string; roles: string[] };
    return { ok: true, detail: `${body.service} sees roles: ${body.roles.join(", ")}` };
  } catch {
    return { ok: false, detail: "service not reachable (is the apps profile running?)" };
  }
}

export async function AreaDashboard({ title, subtitle, upcoming, check }: Props) {
  const session = await auth();
  const result = await platformCheck(check.path);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="text-slate-600">{subtitle}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <section className="card md:col-span-2">
          <h2 className="font-semibold">Coming in Phase 1–2</h2>
          <ul className="mt-3 divide-y divide-slate-100 text-sm">
            {upcoming.map((item) => (
              <li key={item.feature} className="flex items-center justify-between py-2">
                <span>{item.feature}</span>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{item.owner}</span>
              </li>
            ))}
          </ul>
        </section>

        <section className="card space-y-3 text-sm">
          <h2 className="font-semibold">Signed in as</h2>
          <p>{session?.user.name ?? session?.user.email}</p>
          <p className="flex flex-wrap gap-1">
            {session?.user.roles.map((role) => (
              <span key={role} className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">{role}</span>
            ))}
          </p>
          <div className={`rounded-lg p-3 ${result.ok ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>
            <p className="font-medium">Platform check · {check.service}</p>
            <p className="mt-1 break-words">{result.detail}</p>
          </div>
        </section>
      </div>
    </div>
  );
}
