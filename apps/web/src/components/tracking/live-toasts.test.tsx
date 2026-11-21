import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { TrackingView } from "@/lib/tracking";

let push: ((view: TrackingView) => void) | undefined;
vi.mock("@/lib/tracking-socket", () => ({
  followSession: (onUpdate: (view: TrackingView) => void) => {
    push = onUpdate;
    return () => undefined;
  },
}));

import { LiveToasts, toastFor } from "./live-toasts";

const view = (status: TrackingView["currentStatus"], at: string, city: string | null): TrackingView => ({
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: status,
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [{ status: status!, city, at }],
});

describe("toastFor", () => {
  it("describes the latest event, with its city when there is one", () => {
    expect(toastFor(view("OUT_FOR_DELIVERY", "2026-10-20T14:02:00Z", "Kandy"))?.text).toBe("SD261020K7Q2M9X: Out for delivery, Kandy");
    expect(toastFor(view("DELIVERED", "2026-10-20T15:00:00Z", null))?.text).toBe("SD261020K7Q2M9X: Delivered");
  });

  it("returns nothing without events", () => {
    expect(toastFor({ ...view(null, "", null), events: [] })).toBeNull();
  });
});

describe("LiveToasts", () => {
  it("shows a notice for a parcel update and lets the user dismiss it", () => {
    render(<LiveToasts />);
    act(() => push?.(view("OUT_FOR_DELIVERY", "2026-10-20T14:02:00Z", "Kandy")));
    expect(screen.getByText("SD261020K7Q2M9X: Out for delivery, Kandy")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Dismiss" }));
    expect(screen.queryByText("SD261020K7Q2M9X: Out for delivery, Kandy")).not.toBeInTheDocument();
  });

  it("does not repeat the same update", () => {
    render(<LiveToasts />);
    const update = view("DELIVERED", "2026-10-20T15:00:00Z", "Kandy");
    act(() => push?.(update));
    act(() => push?.(update));
    expect(screen.getAllByText("SD261020K7Q2M9X: Delivered, Kandy")).toHaveLength(1);
  });
});
