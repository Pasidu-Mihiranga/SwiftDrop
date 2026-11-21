import type { AuthenticatedUser } from "@swiftdrop/common";
import type { Timeline } from "../../timeline/domain/timeline";

/**
 * Who may read the full timeline of a parcel: the customer, the assigned courier (both in ownerIds),
 * the merchant that sent the parcel (merchant_id claim), and dispatchers and admins.
 * Everyone else gets "not found", so the existence of a parcel is not revealed.
 */
export function canViewTimeline(timeline: Timeline, user: AuthenticatedUser): boolean {
  if (user.roles.includes("DISPATCHER") || user.roles.includes("ADMIN")) return true;
  if (timeline.ownerIds.includes(user.id)) return true;
  return Boolean(user.merchantId) && timeline.merchantId === user.merchantId;
}
