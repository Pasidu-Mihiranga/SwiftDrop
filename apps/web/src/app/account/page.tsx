import Link from "next/link";
import { gatewayFetch } from "@/lib/gateway";
import { districtsFromZones, type SavedAddress } from "@/lib/addresses";
import type { Profile } from "@/lib/profile";
import { AddressBook } from "./address-book";
import { ProfileForm } from "./profile-form";

export const metadata = { title: "My profile" };

const UNAVAILABLE = "Your profile is not available right now. Please try again in a moment.";

async function loadProfile(): Promise<{ profile: Profile } | { error: string }> {
  try {
    const response = await gatewayFetch("/api/v1/users/me", { headers: { Accept: "application/json" } });
    if (response.ok) return { profile: (await response.json()) as Profile };
    if (response.status === 401) return { error: "Your session has expired. Sign in again to see your profile." };
    if (response.status === 403) return { error: "This account cannot have a profile." };
    return { error: UNAVAILABLE };
  } catch {
    return { error: UNAVAILABLE };
  }
}

async function loadAddresses(): Promise<SavedAddress[] | null> {
  try {
    const response = await gatewayFetch("/api/v1/users/me/addresses", { headers: { Accept: "application/json" } });
    return response.ok ? ((await response.json()) as SavedAddress[]) : null;
  } catch {
    return null;
  }
}

/** pricing-service owns the 25 districts; when it is not running the form falls back to a text box. */
async function loadDistricts(): Promise<string[]> {
  try {
    const response = await gatewayFetch("/api/v1/pricing/zones", { headers: { Accept: "application/json" } });
    return response.ok ? districtsFromZones(await response.json()) : [];
  } catch {
    return [];
  }
}

/** Any signed-in user (proxy.ts: /account is "any-authenticated"). The first visit creates the profile. */
export default async function AccountPage() {
  // The profile first: on a first visit it creates the row the address book belongs to.
  const result = await loadProfile();
  const [addresses, districts] = "profile" in result ? await Promise.all([loadAddresses(), loadDistricts()]) : [null, []];

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <header>
        <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
          <Link href="/" className="hover:text-slate-800">
            SwiftDrop
          </Link>
          <span className="mx-1.5 text-slate-300">/</span>
          <span className="text-slate-700">Account</span>
        </nav>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
          My profile &amp; address book
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Your name, phone number and saved addresses, used on deliveries and notifications.
        </p>
      </header>

      {"profile" in result ? (
        <>
          <ProfileForm initial={result.profile} />
          {addresses ? (
            <AddressBook addresses={addresses} districts={districts} />
          ) : (
            <div role="alert" className="card border-amber-200 bg-amber-50 text-sm text-amber-900">
              Your address book is not available right now. Please try again in a moment.
            </div>
          )}
        </>
      ) : (
        <div role="alert" className="card border-amber-200 bg-amber-50 text-sm text-amber-900">
          {result.error}
        </div>
      )}
    </div>
  );
}
