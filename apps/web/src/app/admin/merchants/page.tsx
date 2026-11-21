import Link from "next/link";
import { MerchantDecisions } from "@/components/merchant-decision-forms";
import {
  MERCHANT_STATUSES,
  formatAddress,
  formatDateTime,
  parseQueueQuery,
  statusLabel,
  statusPillClass,
  type Merchant,
  type MerchantStatus,
} from "@/lib/merchant";
import { fetchMerchantQueue } from "@/lib/merchant-api";

export const metadata = { title: "Merchants" };

/**
 * The merchant approval queue (ADMIN). Applications are listed oldest first so they are reviewed in
 * the order they arrived; the other tabs show what has been decided.
 */
export default async function MerchantsAdminPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; page?: string }>;
}) {
  const { status, page } = parseQueueQuery(await searchParams);
  const queue = await fetchMerchantQueue(status, page);
  const meta = queue.page;

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Merchants</h1>
        <p className="text-slate-600">
          Review applications from online stores. Approving one gives the business access to the merchant API.
        </p>
      </div>

      <nav aria-label="Application status" className="flex flex-wrap gap-1 text-sm">
        {MERCHANT_STATUSES.map((tab) => (
          <Link
            key={tab}
            href={`/admin/merchants?status=${tab}`}
            aria-current={tab === status ? "page" : undefined}
            className={tab === status ? "btn-primary" : "btn-secondary"}
          >
            {statusLabel(tab)}
          </Link>
        ))}
      </nav>

      <section className="card space-y-3" aria-label={statusLabel(status)}>
        <p className="text-sm text-slate-600">
          {meta.totalElements} {meta.totalElements === 1 ? "application" : "applications"}
        </p>
        {queue.content.length === 0 ? (
          <p className="notice-info">{emptyMessage(status)}</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {queue.content.map((merchant) => (
              <MerchantRow key={merchant.id} merchant={merchant} />
            ))}
          </ul>
        )}

        {meta.totalPages > 1 ? (
          <nav aria-label="Pages" className="flex items-center gap-2 text-sm">
            <PageLink status={status} page={meta.page - 1} disabled={meta.page === 0} label="Previous" />
            <span className="text-slate-600">
              Page {meta.page + 1} of {meta.totalPages}
            </span>
            <PageLink status={status} page={meta.page + 1} disabled={meta.page + 1 >= meta.totalPages} label="Next" />
          </nav>
        ) : null}
      </section>
    </div>
  );
}

function MerchantRow({ merchant }: { merchant: Merchant }) {
  return (
    <li className="grid gap-3 py-4 sm:grid-cols-[1fr_auto]">
      <div className="space-y-1 text-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{merchant.businessName}</span>
          <span className={statusPillClass(merchant.status)}>{statusLabel(merchant.status)}</span>
        </div>
        <p className="text-slate-600">BR {merchant.brNumber}</p>
        <p className="break-all text-slate-600">
          {merchant.contactEmail}, {merchant.contactPhone}
          {merchant.website ? `, ${merchant.website}` : ""}
        </p>
        <p className="text-slate-600">Pickup: {formatAddress(merchant.pickupAddress)}</p>
        <p className="text-xs text-slate-500">
          Applied {formatDateTime(merchant.createdAt)}
          {merchant.reviewedAt ? `, decided ${formatDateTime(merchant.reviewedAt)}` : ""}
        </p>
        {merchant.rejectionReason ? <p className="text-xs text-slate-600">Reason: {merchant.rejectionReason}</p> : null}
        <p className="break-all text-xs text-slate-400">Merchant id {merchant.id}</p>
      </div>
      <MerchantDecisions merchantId={merchant.id} businessName={merchant.businessName} status={merchant.status} />
    </li>
  );
}

function PageLink({ status, page, disabled, label }: {
  status: MerchantStatus;
  page: number;
  disabled: boolean;
  label: string;
}) {
  if (disabled) {
    return <span className="btn-secondary opacity-50" aria-disabled="true">{label}</span>;
  }
  return (
    <Link className="btn-secondary" href={`/admin/merchants?status=${status}&page=${page}`}>
      {label}
    </Link>
  );
}

function emptyMessage(status: MerchantStatus): string {
  switch (status) {
    case "PENDING":
      return "No applications are waiting for review.";
    case "APPROVED":
      return "No merchants are approved yet.";
    case "REJECTED":
      return "No applications have been rejected.";
    case "SUSPENDED":
      return "No merchants are suspended.";
  }
}
