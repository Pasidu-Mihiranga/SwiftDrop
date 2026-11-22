import Link from "next/link";
import { MerchantRegisterForm } from "@/components/merchant-register-form";
import { districtsFromZones } from "@/lib/addresses";
import { formatAddress, formatDateTime, statusLabel, statusPillClass, type Merchant } from "@/lib/merchant";
import { fetchMyApplication } from "@/lib/merchant-api";
import { fetchActiveZones } from "@/lib/pricing-api";

export const metadata = { title: "Become a merchant" };

/**
 * Any signed-in user can apply; an admin reviews every application (M7). The page shows where the
 * caller's latest application stands, and offers the form when there is none or it was rejected.
 */
export default async function MerchantRegisterPage() {
  const [application, { zones }] = await Promise.all([fetchMyApplication(), fetchActiveZones()]);
  const districts = districtsFromZones(zones);

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <header>
        <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
          <Link href="/" className="hover:text-slate-800">
            SwiftDrop
          </Link>
          <span className="mx-1.5 text-slate-300">/</span>
          <span className="text-slate-700">Merchant onboarding</span>
        </nav>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
          Become a SwiftDrop merchant
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Register your business to create deliveries from your online store through our API. An administrator
          reviews every application before API access is granted.
        </p>
      </header>

      {application ? <ApplicationStatus application={application} /> : null}

      {!application || application.status === "REJECTED" ? (
        <section className="space-y-3 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 sm:p-6">
          <h2 className="font-semibold text-slate-900">
            {application ? "Correct your details and apply again" : "Your application"}
          </h2>
          <MerchantRegisterForm districts={districts} previous={application ?? undefined} />
        </section>
      ) : null}
    </div>
  );
}

function ApplicationStatus({ application }: { application: Merchant }) {
  return (
    <section className="card space-y-3" aria-labelledby="application-heading">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="application-heading" className="font-semibold">
          {application.businessName}
        </h2>
        <span className={statusPillClass(application.status)}>{statusLabel(application.status)}</span>
      </div>

      <StatusMessage application={application} />

      <dl className="grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
        <dt className="text-slate-500">Registration number</dt>
        <dd>{application.brNumber}</dd>
        <dt className="text-slate-500">Contact</dt>
        <dd className="break-all">
          {application.contactEmail}, {application.contactPhone}
        </dd>
        <dt className="text-slate-500">Pickup address</dt>
        <dd>{formatAddress(application.pickupAddress)}</dd>
        <dt className="text-slate-500">Applied</dt>
        <dd>{formatDateTime(application.createdAt)}</dd>
        {application.reviewedAt ? (
          <>
            <dt className="text-slate-500">Decided</dt>
            <dd>{formatDateTime(application.reviewedAt)}</dd>
          </>
        ) : null}
      </dl>
    </section>
  );
}

function StatusMessage({ application }: { application: Merchant }) {
  switch (application.status) {
    case "PENDING":
      return <p className="notice-info">Your application is waiting for an administrator. Check back here for the decision.</p>;
    case "APPROVED":
      return (
        <p className="notice-success">
          Your business is approved. Sign out and in again to open the merchant portal, where your API credentials
          will appear.
        </p>
      );
    case "REJECTED":
      return (
        <div className="notice-error" role="alert">
          <p className="font-medium">Your application was not approved.</p>
          {application.rejectionReason ? <p>Reason: {application.rejectionReason}</p> : null}
        </div>
      );
    case "SUSPENDED":
      return (
        <p className="notice-warning">
          This merchant account is suspended, so orders through the API are refused. Contact SwiftDrop support.
        </p>
      );
  }
}
