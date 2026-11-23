import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { TrackingView } from "@/lib/tracking";

vi.mock("@/lib/tracking-socket", () => ({ followSession: () => () => undefined }));

import { ParcelTimeline, TimelineDisclosure } from "./parcel-timeline";

const view: TrackingView = {
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: "OUT_FOR_DELIVERY",
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [{ status: "OUT_FOR_DELIVERY", city: "Kandy", at: "2026-11-22T09:00:00Z" }],
};

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

describe("ParcelTimeline", () => {
  it("loads the owner timeline through the BFF and shows it live", async () => {
    const fetchImpl = vi.fn(async () => json(view));
    render(<ParcelTimeline parcelId="p-1" fetchImpl={fetchImpl} />);

    expect(screen.getByText("Loading live timeline...")).toBeInTheDocument();
    expect(await screen.findByText("Out for delivery, Kandy")).toBeInTheDocument();
    expect(fetchImpl).toHaveBeenCalledWith("/api/bff/api/v1/tracking/parcels/p-1/timeline", { cache: "no-store" });
  });

  it("explains a timeline that does not exist yet and one that cannot load", async () => {
    const { unmount } = render(<ParcelTimeline parcelId="p-1" fetchImpl={vi.fn(async () => json({}, 404))} />);
    expect(await screen.findByText(/appears once the parcel's first update/)).toBeInTheDocument();
    unmount();

    render(<ParcelTimeline parcelId="p-2" fetchImpl={vi.fn(async () => { throw new Error("offline"); })} />);
    expect(await screen.findByText("The live timeline is not available right now.")).toBeInTheDocument();
  });

  it("shows a server error plainly", async () => {
    render(<ParcelTimeline parcelId="p-3" fetchImpl={vi.fn(async () => json({}, 503))} />);
    expect(await screen.findByText("The live timeline is not available right now.")).toBeInTheDocument();
  });
});

describe("TimelineDisclosure", () => {
  it("loads the timeline only when opened", async () => {
    const fetchMock = vi.fn(async () => json(view));
    vi.stubGlobal("fetch", fetchMock);
    const { container } = render(<TimelineDisclosure parcelId="p-9" />);
    expect(fetchMock).not.toHaveBeenCalled();

    const details = container.querySelector("details")!;
    details.open = true;
    fireEvent(details, new Event("toggle"));

    expect(await screen.findByText("Out for delivery, Kandy")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    vi.unstubAllGlobals();
  });
});
