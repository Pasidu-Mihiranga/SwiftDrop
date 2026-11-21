import { Controller, Get, Param, UseGuards } from "@nestjs/common";
import { ApiError, type AuthenticatedUser } from "@swiftdrop/common";
import { CurrentUser, Public } from "@swiftdrop/common/nest";
import { RateLimitGuard } from "./rate-limit.guard";
import { TrackingQueryService } from "./tracking.query.service";

// Same shape as the OpenAPI pattern. Anything else is reported as "not found" (no enumeration hints).
const TRACKING_NUMBER = /^SD[0-9A-Z]{8,20}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

@Controller("api/v1/tracking")
export class TrackingController {
  constructor(private readonly tracking: TrackingQueryService) {}

  /** Public, PII-masked view: city names and a masked recipient name only. Rate limited per client IP. */
  @Get("public/:trackingNumber")
  @Public()
  @UseGuards(RateLimitGuard)
  async publicView(@Param("trackingNumber") trackingNumber: string) {
    const view = TRACKING_NUMBER.test(trackingNumber) ? await this.tracking.publicView(trackingNumber) : null;
    if (!view) throw new ApiError(404, "NOT_FOUND", "No parcel has this tracking number.");
    return view;
  }

  /** Full timeline for the customer, the assigned courier, the merchant that sent it, and staff. */
  @Get("parcels/:parcelId/timeline")
  async ownerTimeline(@Param("parcelId") parcelId: string, @CurrentUser() user: AuthenticatedUser) {
    const view = UUID.test(parcelId) ? await this.tracking.ownerTimeline(parcelId, user) : null;
    if (!view) throw new ApiError(404, "NOT_FOUND", "No parcel with this id is visible to you.");
    return view;
  }
}
