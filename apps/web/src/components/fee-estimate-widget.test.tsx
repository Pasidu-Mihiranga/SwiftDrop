import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FeeEstimateWidget } from "./fee-estimate-widget";
import type { Quote, Zone } from "@/lib/pricing";

const ZONES: Zone[] = [
  { code: "WP", name: "Western", districts: ["Colombo", "Gampaha"], active: true },
  { code: "CP", name: "Central", districts: ["Kandy"], active: true },
];

const NOW = new Date("2026-06-01T09:00:00Z");

function quote(overrides: Partial<Quote> = {}): Quote {
  return {
    quoteId: "7f1c0d9e-0000-4000-8000-000000000001",
    fee: { amount: 631.0, currency: "LKR" },
    breakdown: [
      { rule: "BASE", amount: 475.0 },
      { rule: "WEIGHT", amount: 156.0 },
    ],
    fromZone: "WP",
    toZone: "CP",
    weightKg: 2,
    sizeClass: "M",
    serviceLevel: "STANDARD",
    issuedAt: "2026-06-01T09:00:00Z",
    expiresAt: "2026-06-01T09:15:00Z",
    ...overrides,
  };
}

function problemResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

/** Fills the three required fields; returns the user-event instance for further interaction. */
async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText("Collect from"), "Colombo");
  await user.selectOptions(screen.getByLabelText("Deliver to"), "Kandy");
  await user.type(screen.getByLabelText("Weight (kg)"), "2");
}

beforeEach(() => {
  vi.useFakeTimers({ now: NOW, toFake: ["Date", "setInterval", "clearInterval"] });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("FeeEstimateWidget", () => {
  it("prompts for input before anything has been requested", () => {
    render(<FeeEstimateWidget zones={ZONES} />);

    expect(screen.getByText(/pick two districts and a weight/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get estimate" })).toBeEnabled();
  });

  it("offers only the districts the API advertises", () => {
    render(<FeeEstimateWidget zones={ZONES} />);

    const options = within(screen.getByLabelText("Collect from")).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "Select a district",
      "Colombo",
      "Gampaha",
      "Kandy",
    ]);
  });

  it("disables the district pickers and explains itself when coverage could not be loaded", () => {
    render(<FeeEstimateWidget zones={[]} coverageError="We could not load our service areas." />);

    expect(screen.getByRole("alert")).toHaveTextContent("We could not load our service areas.");
    expect(screen.getByLabelText("Collect from")).toBeDisabled();
    expect(screen.getByLabelText("Deliver to")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Get estimate" })).toBeDisabled();
    expect(screen.getAllByText("Coverage unavailable")).toHaveLength(2);
  });

  it("validates in the browser without calling the service", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<FeeEstimateWidget zones={ZONES} />);

    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByText(/choose a collection district/i)).toBeInTheDocument();
    expect(screen.getByText(/choose a delivery district/i)).toBeInTheDocument();
    expect(screen.getByText(/enter the parcel weight/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("clears a field error as soon as the field is corrected", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal("fetch", vi.fn());
    render(<FeeEstimateWidget zones={ZONES} />);

    await user.click(screen.getByRole("button", { name: "Get estimate" }));
    expect(await screen.findByText(/choose a collection district/i)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Collect from"), "Colombo");

    expect(screen.queryByText(/choose a collection district/i)).not.toBeInTheDocument();
  });

  it("shows a pending state while the estimate is being calculated", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    let release: (value: Response) => void = () => {};
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise<Response>((resolve) => (release = resolve))));
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByText(/calculating your fee/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Calculating..." })).toBeDisabled();

    release(new Response(JSON.stringify(quote()), { status: 200, headers: { "content-type": "application/json" } }));
    await waitFor(() => expect(screen.queryByText(/calculating your fee/i)).not.toBeInTheDocument());
  });

  it("posts the validated request to the proxy and renders the fee breakdown", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(quote()), { status: 200, headers: { "content-type": "application/json" } }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const onQuote = vi.fn();
    render(<FeeEstimateWidget zones={ZONES} onQuote={onQuote} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    await waitFor(() => expect(screen.getAllByText("LKR 631.00")).toHaveLength(2));
    expect(screen.getByText("Base rate")).toBeInTheDocument();
    expect(screen.getByText("LKR 475.00")).toBeInTheDocument();
    expect(screen.getByText("Weight")).toBeInTheDocument();
    expect(screen.getByText("LKR 156.00")).toBeInTheDocument();
    expect(screen.getByText(/zone WP to zone CP/i)).toBeInTheDocument();
    expect(screen.getByText(/^Valid for /)).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/bff/api/v1/pricing/quotes");
    expect(JSON.parse(init.body as string)).toEqual({
      fromDistrict: "Colombo",
      toDistrict: "Kandy",
      weightKg: 2,
      sizeClass: "M",
      serviceLevel: "STANDARD",
    });
    expect(onQuote).toHaveBeenCalledWith(quote());
  });

  it("hands the quote reference on so a booking flow can use it", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(quote()), { status: 200, headers: { "content-type": "application/json" } }),
      ),
    );
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByText(/7f1c0d9e-0000-4000-8000-000000000001/)).toBeInTheDocument();
  });

  it("explains a route with no published rate in plain words", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        problemResponse(422, { status: 422, code: "NO_RATE_CARD", detail: "No rate card is in force for WP to CP" }),
      ),
    );
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/no published rate/i);
  });

  it("keeps a server failure vague rather than leaking internals", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        problemResponse(500, { status: 500, code: "INTERNAL", detail: "NullPointerException at line 42" }),
      ),
    );
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/having trouble/i);
    expect(alert).not.toHaveTextContent(/NullPointerException/);
  });

  it("reports an unreachable service instead of failing silently", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/could not reach the pricing service/i);
  });

  it("surfaces a field-level message the service sent back", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        problemResponse(400, {
          status: 400,
          code: "VALIDATION_FAILED",
          errors: [{ field: "weightKg", message: "must be 100 or less" }],
        }),
      ),
    );
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByText("must be 100 or less")).toBeInTheDocument();
  });

  it("marks the estimate expired once its lifetime runs out", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(quote({ expiresAt: "2026-06-01T09:00:02Z" })), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      ),
    );
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));
    expect(await screen.findByText(/^Valid for /)).toBeInTheDocument();
    expect(screen.queryByText(/estimate expired/i)).not.toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(3000));

    expect(await screen.findByText(/estimate expired/i)).toBeInTheDocument();
    expect(screen.getByText(/recalculate to continue/i)).toBeInTheDocument();
    expect(screen.queryByText(/^Valid for /)).not.toBeInTheDocument();
    // The fee stays on screen: the customer should see what has lapsed, not a blank panel.
    expect(screen.getAllByText("LKR 631.00")).toHaveLength(2);
  });

  it("offers a recalculation once an estimate exists", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(quote()), { status: 200, headers: { "content-type": "application/json" } }),
      ),
    );
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    expect(await screen.findByRole("button", { name: "Recalculate" })).toBeInTheDocument();
  });

  it("includes cash on delivery only when the customer asks for it", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(quote()), { status: 200, headers: { "content-type": "application/json" } }),
    );
    vi.stubGlobal("fetch", fetchMock);
    render(<FeeEstimateWidget zones={ZONES} />);

    await fillForm(user);
    await user.type(screen.getByLabelText(/cash to collect/i), "1500");
    await user.selectOptions(screen.getByLabelText("Service"), "EXPRESS");
    await user.click(screen.getByRole("button", { name: "Get estimate" }));

    await waitFor(() => expect(screen.getAllByText("LKR 631.00")).toHaveLength(2));
    expect(JSON.parse(fetchMock.mock.calls[0][1].body as string)).toMatchObject({
      codAmount: 1500,
      serviceLevel: "EXPRESS",
    });
  });
});
