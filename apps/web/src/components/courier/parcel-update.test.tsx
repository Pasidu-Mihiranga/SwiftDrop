import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ParcelUpdate } from "./parcel-update";
import * as courier from "@/lib/courier";

const fetchMock = vi.fn<typeof fetch>();

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

function parcel(status: courier.ParcelStatus) {
  return {
    id: "p-1",
    trackingNumber: "SD261122NY9D6EJ",
    status,
    dropoff: { line1: "45 Peradeniya Road", city: "Kandy" },
    recipient: { name: "Saman Perera", phone: "0712345678" },
    notes: "Leave at reception",
  };
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("ParcelUpdate", () => {
  it("shows the parcel, the recipient to call and only the valid next step", async () => {
    fetchMock.mockResolvedValueOnce(json(parcel("CREATED")));
    render(<ParcelUpdate parcelId="p-1" />);

    expect(await screen.findByRole("heading", { name: "SD261122NY9D6EJ" })).toBeInTheDocument();
    expect(screen.getByText("45 Peradeniya Road, Kandy")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Call 0712345678" })).toHaveAttribute("href", "tel:0712345678");
    expect(screen.getByRole("button", { name: "Picked up" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delivered" })).not.toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Progress" })).toBeInTheDocument();
  });

  it("moves on at once and keeps the server's answer", async () => {
    fetchMock.mockResolvedValueOnce(json(parcel("CREATED"))).mockResolvedValueOnce(json(parcel("PICKED_UP")));
    render(<ParcelUpdate parcelId="p-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Picked up" }));

    expect(await screen.findByRole("button", { name: "In transit" })).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/bff/api/v1/courier/parcels/p-1/status");
    expect(JSON.parse(String(init?.body))).toEqual({ toStatus: "PICKED_UP" });
  });

  it("rolls back and explains when the server refuses", async () => {
    fetchMock
      .mockResolvedValueOnce(json(parcel("CREATED")))
      .mockResolvedValueOnce(json({ code: "INVALID_STATUS_TRANSITION" }, 422));
    render(<ParcelUpdate parcelId="p-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Picked up" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/not possible any more/);
    expect(screen.getByRole("button", { name: "Picked up" })).toBeInTheDocument();
  });

  it("reports a failed delivery only with a reason", async () => {
    fetchMock.mockResolvedValueOnce(json(parcel("OUT_FOR_DELIVERY"))).mockResolvedValueOnce(json(parcel("FAILED")));
    render(<ParcelUpdate parcelId="p-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Could not deliver" }));
    await userEvent.click(screen.getByRole("button", { name: "Report failed delivery" }));
    expect(screen.getByRole("alert")).toHaveTextContent(/why the delivery failed/);

    await userEvent.click(screen.getByRole("radio", { name: "Recipient refused it" }));
    await userEvent.type(screen.getByRole("textbox", { name: "Note" }), "Said not ordered");
    await userEvent.click(screen.getByRole("button", { name: "Report failed delivery" }));

    expect(await screen.findByText("Dispatch decides when this parcel goes out again.")).toBeInTheDocument();
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({
      toStatus: "FAILED", failureReason: "REFUSED", note: "Said not ordered",
    });
  });

  it("delivers with the recipient's name", async () => {
    fetchMock.mockResolvedValueOnce(json(parcel("OUT_FOR_DELIVERY"))).mockResolvedValueOnce(json(parcel("DELIVERED")));
    render(<ParcelUpdate parcelId="p-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Delivered" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirm delivery" }));
    expect(screen.getByRole("alert")).toHaveTextContent(/who received/);

    await userEvent.type(screen.getByRole("textbox", { name: "Received by" }), "Saman");
    await userEvent.click(screen.getByRole("button", { name: "Confirm delivery" }));

    expect(await screen.findByText("Nothing more to do for this parcel.")).toBeInTheDocument();
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body)).proof).toEqual({ type: "NAME", receivedBy: "Saman" });
  });

  it("uploads a photo straight to storage and delivers with it", async () => {
    const upload = vi.spyOn(courier, "uploadProof").mockImplementation(async (_url, _file, onProgress) => {
      onProgress(40);
      onProgress(100);
    });
    fetchMock
      .mockResolvedValueOnce(json(parcel("OUT_FOR_DELIVERY")))
      .mockResolvedValueOnce(json({ proofId: "proof-9", uploadUrl: "https://s3/put", expiresAt: "2026-11-22T10:00:00Z" }))
      .mockResolvedValueOnce(json(parcel("DELIVERED")));
    render(<ParcelUpdate parcelId="p-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Delivered" }));
    const photo = new File(["jpeg"], "door.jpg", { type: "image/jpeg" });
    await userEvent.upload(screen.getByLabelText(/Photo/), photo);

    expect(await screen.findByText("Photo uploaded.")).toBeInTheDocument();
    expect(upload).toHaveBeenCalledWith("https://s3/put", photo, expect.any(Function));
    await userEvent.click(screen.getByRole("button", { name: "Confirm delivery" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(JSON.parse(String(fetchMock.mock.calls[2][1]?.body)).proof).toEqual({ type: "PHOTO", proofId: "proof-9" });
  });

  it("refuses a photo the server would refuse and explains an upload failure", async () => {
    vi.spyOn(courier, "uploadProof").mockRejectedValue(new courier.CourierError("UPLOAD_FAILED", "The photo could not be uploaded. Check your signal."));
    fetchMock
      .mockResolvedValueOnce(json(parcel("OUT_FOR_DELIVERY")))
      .mockResolvedValueOnce(json({ proofId: "proof-9", uploadUrl: "https://s3/put", expiresAt: "x" }));
    render(<ParcelUpdate parcelId="p-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "Delivered" }));
    await userEvent.upload(screen.getByLabelText(/Photo/), new File(["x"], "doc.pdf", { type: "application/pdf" }), { applyAccept: false });
    expect(screen.getByRole("alert")).toHaveTextContent(/JPEG or PNG/);

    await userEvent.upload(screen.getByLabelText(/Photo/), new File(["x"], "door.png", { type: "image/png" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/Check your signal/);
    await userEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByRole("button", { name: "Delivered" })).toBeInTheDocument();
  });

  it("says when the parcel is not the courier's", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 404 }));
    render(<ParcelUpdate parcelId="p-1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("This parcel is not assigned to you.");
    expect(screen.getByRole("link", { name: "Back to the run sheet" })).toHaveAttribute("href", "/courier");
  });
});
