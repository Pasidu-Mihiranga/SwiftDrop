import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { RunSheet } from "./run-sheet";

const fetchMock = vi.fn<typeof fetch>();

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

const PARCEL = {
  parcelId: "p-1", trackingNumber: "SD261122NY9D6EJ", zone: "CP", sizeClass: "S", pickupCity: "Colombo",
  dropoffCity: "Kandy", createdAt: "2026-11-22T08:00:00Z", status: "OUT_FOR_DELIVERY", assignmentId: "a-1",
  courierId: "courier-1", runDate: "2026-11-22",
};

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => vi.unstubAllGlobals());

describe("RunSheet", () => {
  it("lists the courier's parcels with a link to update each", async () => {
    fetchMock.mockResolvedValue(json([PARCEL]));
    render(<RunSheet />);

    expect(await screen.findByText("1 assigned parcels")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /SD261122NY9D6EJ/ })).toHaveAttribute("href", "/track/SD261122NY9D6EJ");
    expect(screen.getByRole("link", { name: "Update" })).toHaveAttribute("href", "/courier/parcels/p-1");
    expect(screen.getByText("Colombo → Kandy")).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/^\/api\/bff\/api\/v1\/courier\/run-sheet\?date=\d{4}-\d{2}-\d{2}$/);
  });

  it("says when nothing is assigned and loads another day on request", async () => {
    fetchMock.mockResolvedValue(json([]));
    render(<RunSheet />);

    expect(await screen.findByText("No parcels assigned for this day.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Run date"), { target: { value: "2026-12-01" } });
    expect(await screen.findByText("No parcels assigned for this day.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("date=2026-12-01"))).toBe(true);
  });

  it("shows why the run sheet could not load", async () => {
    fetchMock.mockResolvedValue(json({ detail: "dispatch-service is down" }, 503));
    render(<RunSheet />);

    expect(await screen.findByRole("alert")).toHaveTextContent("dispatch-service is down");
  });
});
