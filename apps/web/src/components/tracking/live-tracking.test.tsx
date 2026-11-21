import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TrackingView } from "@/lib/tracking";
import type { LiveState } from "@/lib/tracking-socket";

type Listener = (view: TrackingView) => void;
const live: { onUpdate?: Listener; onState?: (state: LiveState) => void; disconnect: ReturnType<typeof vi.fn> } = {
  disconnect: vi.fn(),
};

vi.mock("@/lib/tracking-socket", () => ({
  followParcel: (_number: string, onUpdate: Listener, onState: (state: LiveState) => void) => {
    live.onUpdate = onUpdate;
    live.onState = onState;
    return live.disconnect;
  },
}));

const { LiveTracking } = await import("./live-tracking");

const initial: TrackingView = {
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: "PICKED_UP",
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [{ status: "PICKED_UP", city: "Colombo", at: "2026-10-20T09:00:00Z" }],
};

describe("LiveTracking", () => {
  beforeEach(() => {
    live.disconnect.mockClear();
  });

  it("renders the server-side view first, with its stepper and masked recipient", () => {
    render(<LiveTracking initial={initial} />);
    expect(screen.getByRole("heading", { name: "SD261020K7Q2M9X" })).toBeInTheDocument();
    expect(screen.getByTestId("current-status")).toHaveTextContent("Picked up");
    expect(screen.getByText("K*** P.")).toBeInTheDocument();
    expect(screen.getByText("Connecting...")).toBeInTheDocument();
  });

  it("shows a live push as the new status, and marks the socket as live", () => {
    render(<LiveTracking initial={initial} />);
    act(() => live.onState?.("live"));
    expect(screen.getByText("Live")).toBeInTheDocument();

    act(() =>
      live.onUpdate?.({
        ...initial,
        currentStatus: "OUT_FOR_DELIVERY",
        events: [...initial.events, { status: "OUT_FOR_DELIVERY", city: "Kandy", at: "2026-10-20T14:02:00Z" }],
      }),
    );
    expect(screen.getByTestId("current-status")).toHaveTextContent("Out for delivery");
  });

  it("ignores a late, older push", () => {
    render(<LiveTracking initial={initial} />);
    act(() =>
      live.onUpdate?.({
        ...initial,
        currentStatus: "CREATED",
        events: [{ status: "CREATED", city: "Colombo", at: "2026-10-20T08:00:00Z" }],
      }),
    );
    expect(screen.getByTestId("current-status")).toHaveTextContent("Picked up");
  });

  it("reports reconnection and disconnects on unmount", () => {
    const { unmount } = render(<LiveTracking initial={initial} />);
    act(() => live.onState?.("reconnecting"));
    expect(screen.getByText("Reconnecting...")).toBeInTheDocument();
    unmount();
    expect(live.disconnect).toHaveBeenCalled();
  });
});
