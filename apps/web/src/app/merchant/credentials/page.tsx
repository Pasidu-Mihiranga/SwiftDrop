import Link from "next/link";
import { MerchantCredentialsForm } from "@/components/merchant-credentials-form";
import { MerchantNav } from "@/components/merchant-nav";
import { statusLabel, statusPillClass, type Merchant } from "@/lib/merchant";
import { fetchMyApplication } from "@/lib/merchant-api";

export const metadata = { title: "API credentials" };

/**
 * The merchant's API credentials (M7): the OAuth 2.0 client their own system uses to create
 * orders. SwiftDrop never stores the secret, so this page cannot show an existing one; it issues a
 * new secret and shows it once. The first secret after approval is collected here too.
 */
export default async function MerchantCredentialsPage() {
  let merchant: Merchant | null = null;
  let loadError = false;
  try {
    merchant = await fetchMyApplication();
  } catch {
    loadError = true;
  }

  return (
    <div className="space-y-6">
      <MerchantNav current="/merchant/credentials" />
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">API credentials</h1>
        <p className="text-slate-600">
          Your store&apos;s system signs in with these credentials (OAuth 2.0 client credentials) to send orders to
          SwiftDrop.
        </p>
      </div>

      <section className="card space-y-4">
        {loadError ? (
          <p className="notice-error" role="alert">We could not load your merchant account. Please try again shortly.</p>
        ) : null}
        {merchant ? (
          <p className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium">{merchant.businessName}</span>
            <span className={statusPillClass(merchant.status)}>{statusLabel(merchant.status)}</span>
          </p>
        ) : null}

        {merchant && merchant.status !== "APPROVED" ? (
          <p className="notice-warning">
            {merchant.status === "SUSPENDED"
              ? "This merchant account is suspended, so new secrets cannot be issued and API orders are refused. Contact SwiftDrop support."
              : "API credentials are available once your business is approved."}
          </p>
        ) : null}

        {!loadError && !merchant ? (
          <p className="notice-info">
            There is no merchant application for this account yet, so there are no API credentials to issue.{" "}
            <Link href="/merchant/register" className="font-medium text-brand-700 hover:underline">
              Register your business
            </Link>{" "}
            and they become available here once SwiftDrop approves it.
          </p>
        ) : null}

        {merchant?.status === "APPROVED" ? <MerchantCredentialsForm /> : null}
      </section>
    </div>
  );
}
