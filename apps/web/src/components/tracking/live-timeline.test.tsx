import { act, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { TrackingView } from "@/lib/tracking";

let push: ((view: TrackingView) => void) | undefined;
vi.mock("@/lib/tracking-socket", () => ({
  followSession: (onUpdate: (view: TrackingView) => void) => {
    push = onUpdate;
    return () => undefined;
  },
}));

import { LiveTimeline } from "./live-timeline";

const initial: TrackingView = {
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: "PICKED_UP",
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [{ status: "PICKED_UP", city: "Colombo", at: "2026-10-20T09:00:00Z" }],
};

describe("LiveTimeline", () => {
  it("follows updates for its own parcel only", () => {
    const { container } = render(<LiveTimeline initial={initial} />);
    expect(screen.getByRole("region", { name: "Delivery timeline" })).toBeInTheDocument();
    expect(screen.getByText("Picked up, Colombo")).toBeInTheDocument();

    act(() =>
      push?.({
        ...initial,
        currentStatus: "IN_TRANSIT",
        events: [...initial.events, { status: "IN_TRANSIT", city: "Colombo", at: "2026-10-20T10:00:00Z" }],
      }),
    );
    expect(screen.getByText("In transit, Colombo")).toBeInTheDocument();

    // An update for another parcel must not change this timeline: the event list keeps its two entries.
    act(() => push?.({ ...initial, trackingNumber: "SD999999999999", currentStatus: "DELIVERED" }));
    expect(container.querySelectorAll("ul > li")).toHaveLength(2);
    expect(container.querySelector('li[aria-current="step"]')?.textContent).toContain("In transit");
  });
});
