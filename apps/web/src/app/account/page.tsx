import { gatewayFetch } from "@/lib/gateway";
import type { Profile } from "@/lib/profile";
import { ProfileForm } from "./profile-form";

export const metadata = { title: "My profile" };

async function loadProfile(): Promise<{ profile: Profile } | { error: string }> {
  try {
    const response = await gatewayFetch("/api/v1/users/me", { headers: { Accept: "application/json" } });
    if (response.ok) return { profile: (await response.json()) as Profile };
    if (response.status === 401) return { error: "Your session has expired. Sign in again to see your profile." };
    if (response.status === 403) return { error: "This account cannot have a profile." };
    return { error: "Your profile is not available right now. Please try again in a moment." };
  } catch {
    return { error: "Your profile is not available right now. Please try again in a moment." };
  }
}

/** Any signed-in user (proxy.ts: /account is "any-authenticated"). The first visit creates the profile. */
export default async function AccountPage() {
  const result = await loadProfile();

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">My profile</h1>
        <p className="text-slate-600">Your name and phone number, used on deliveries and notifications.</p>
      </div>

      {"profile" in result ? (
        <ProfileForm initial={result.profile} />
      ) : (
        <div role="alert" className="card border-amber-200 bg-amber-50 text-sm text-amber-900">
          {result.error}
        </div>
      )}
    </div>
  );
}
