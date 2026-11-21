import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import type { CourierReport, Day, Insight, ReportFilter, Totals, ZoneReport } from "@/lib/reports";
import { CourierLeaderboard } from "./courier-leaderboard";
import { DeltaBadge } from "./delta-badge";
import { FilterBar } from "./filter-bar";
import { InsightsCard } from "./insights-card";
import { KpiCards } from "./kpi-cards";
import { PendingFrame, ReportsNavigation, RetryButton, useReportsNavigation } from "./reports-navigation";
import { TrendPanel } from "./trend-panel";
import { ZoneBreakdown } from "./zone-breakdown";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

function withNavigation(ui: ReactNode) {
  return render(<ReportsNavigation>{ui}</ReportsNavigation>);
}

const FILTER: ReportFilter = { from: "2026-10-01", to: "2026-10-07", preset: "7d" };

function day(date: string, delivered: number, failed: number, created = 5, cancelled = 0): Day {
  return { date, created, delivered, failed, cancelled };
}

const DAYS: Day[] = [
  day("2026-10-01", 4, 1),
  day("2026-10-02", 6, 0),
  day("2026-10-03", 2, 2),
  day("2026-10-04", 9, 1, 8, 1),
];
const PREVIOUS_DAYS: Day[] = [
  day("2026-09-27", 3, 1),
  day("2026-09-28", 3, 1),
  day("2026-09-29", 1, 0),
  day("2026-09-30", 5, 2),
];
const TOTALS: Totals = { created: 23, delivered: 21, failed: 4, cancelled: 1, successRate: 21 / 25 };
const PREVIOUS: Totals = { created: 20, delivered: 12, failed: 4, cancelled: 2, successRate: 0.75 };

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
  // jsdom has no PointerEvent; without it fireEvent.pointer* drops clientX.
  if (!("PointerEvent" in window)) vi.stubGlobal("PointerEvent", class extends MouseEvent {});
  vi.stubGlobal(
    "ResizeObserver",
    class {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe() {
        this.callback([{ contentRect: { width: 600 } } as ResizeObserverEntry], this as unknown as ResizeObserver);
      }
      disconnect() {}
    },
  );
});

afterEach(() => vi.unstubAllGlobals());

describe("DeltaBadge", () => {
  it("says when there is nothing to compare with", () => {
    render(<DeltaBadge delta={null} />);
    expect(screen.getByText("No earlier data")).toBeInTheDocument();
  });

  it("spells out the direction and the comparison", () => {
    render(<DeltaBadge delta={{ direction: "up", text: "12%", tone: "good" }} label="vs last week" />);
    expect(screen.getByTitle("Up 12% vs last week")).toBeInTheDocument();
    expect(screen.getByText("vs last week")).toBeInTheDocument();
  });

  it("reads a fall and a flat change", () => {
    const { rerender } = render(<DeltaBadge delta={{ direction: "down", text: "3", tone: "bad" }} />);
    expect(screen.getByTitle("Down 3")).toBeInTheDocument();
    rerender(<DeltaBadge delta={{ direction: "flat", text: "0", tone: "neutral" }} />);
    expect(screen.getByTitle("0")).toBeInTheDocument();
  });
});

describe("KpiCards", () => {
  it("leads with the success rate and how it was counted", () => {
    render(<KpiCards totals={TOTALS} previous={PREVIOUS} days={DAYS} comparisonLabel="vs previous 7 days" />);
    expect(screen.getByRole("meter", { name: "Success rate" })).toHaveAttribute("aria-valuenow", "84");
    expect(screen.getByText("21 of 25 delivery attempts succeeded.")).toBeInTheDocument();
    for (const label of ["Delivered", "New parcels", "Failed attempts", "Cancelled"]) {
      expect(screen.getByRole("img", { name: `${label} per day` })).toBeInTheDocument();
    }
  });

  it("handles a period without attempts or history", () => {
    const empty: Totals = { created: 0, delivered: 0, failed: 0, cancelled: 0, successRate: null };
    render(<KpiCards totals={empty} previous={null} days={[DAYS[0]]} comparisonLabel="vs previous 7 days" />);
    expect(screen.getByText("No delivery attempts in this period.")).toBeInTheDocument();
    expect(screen.getAllByText("No earlier data")).toHaveLength(5);
    expect(screen.queryByRole("img", { name: /per day/ })).not.toBeInTheDocument();
  });
});

describe("ReportsNavigation", () => {
  it("refuses to be used outside the provider", () => {
    function Orphan() {
      useReportsNavigation();
      return null;
    }
    vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Orphan />)).toThrow(/inside <ReportsNavigation>/);
  });

  it("retries by refreshing the route", async () => {
    withNavigation(
      <PendingFrame>
        <RetryButton />
      </PendingFrame>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(refresh).toHaveBeenCalledOnce();
  });
});

describe("InsightsCard", () => {
  const insights: Insight[] = [
    { id: "a", kind: "risk", title: "Central is slipping", detail: "7 of 12 failed", zone: "CP" },
    { id: "b", kind: "peak", title: "Busiest day", detail: "4 Oct" },
  ];

  it("lets the reader focus on the zone behind a finding", async () => {
    withNavigation(<InsightsCard insights={insights} filter={FILTER} />);
    expect(screen.getByText("Busiest day")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Focus on this zone" }));
    expect(push).toHaveBeenCalledWith(expect.stringContaining("zone=CP"), { scroll: false });
  });

  it("explains an empty period", () => {
    withNavigation(<InsightsCard insights={[]} filter={FILTER} />);
    expect(screen.getByText(/Nothing stands out yet/)).toBeInTheDocument();
  });
});

describe("FilterBar", () => {
  const zones = [
    { code: "WP", activity: 12 },
    { code: "CP", activity: null },
  ];
  const names = { WP: "Western", CP: "Central" };

  it("switches the period preset and keeps the zone", async () => {
    withNavigation(<FilterBar filter={{ ...FILTER, zone: "WP" }} zones={zones} zoneNames={names} loadedAt="09:30" />);
    expect(screen.getByText("Updated 09:30")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Last 7 days" }));
    expect(push).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Last 30 days" }));
    expect(push).toHaveBeenCalledWith("?range=30d&zone=WP", { scroll: false });
  });

  it("applies a valid custom range and rejects a reversed one", async () => {
    const user = userEvent.setup();
    withNavigation(<FilterBar filter={FILTER} zones={zones} zoneNames={names} loadedAt="09:30" />);
    await user.click(screen.getByRole("button", { name: "Custom" }));
    const dialog = screen.getByRole("dialog", { name: "Custom date range" });
    const [from, to] = within(dialog).getAllByDisplayValue(/2026-10-0/);

    fireEvent.change(from, { target: { value: "2026-10-09" } });
    expect(within(dialog).getByRole("alert")).toHaveTextContent("Pick a start on or before the end");
    expect(within(dialog).getByRole("button", { name: "Apply" })).toBeDisabled();

    fireEvent.change(from, { target: { value: "2026-09-20" } });
    fireEvent.change(to, { target: { value: "2026-09-25" } });
    await user.click(within(dialog).getByRole("button", { name: "Apply" }));
    expect(push).toHaveBeenCalledWith(expect.stringContaining("from=2026-09-20"), { scroll: false });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("closes the custom range on Cancel, Escape and an outside press", async () => {
    const user = userEvent.setup();
    withNavigation(<FilterBar filter={{ ...FILTER, preset: "custom" }} zones={zones} zoneNames={names} loadedAt="09:30" />);
    const toggle = screen.getByRole("button", { expanded: false, name: /Oct/ });
    await user.click(toggle);
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(toggle);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(toggle);
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("searches, picks and clears a zone", async () => {
    const user = userEvent.setup();
    withNavigation(<FilterBar filter={FILTER} zones={zones} zoneNames={names} loadedAt="09:30" />);
    await user.click(screen.getByRole("button", { name: /All zones/ }));
    expect(within(screen.getByRole("listbox")).getByText("12")).toBeInTheDocument();

    await user.type(screen.getByRole("textbox", { name: "Search zones" }), "nowhere");
    expect(screen.getByText(/No zone matches/)).toBeInTheDocument();
    await user.clear(screen.getByRole("textbox", { name: "Search zones" }));
    await user.type(screen.getByRole("textbox", { name: "Search zones" }), "cent");
    await user.keyboard("{ArrowDown}{ArrowDown}{ArrowUp}{ArrowUp}");
    await user.click(screen.getByRole("button", { name: /Central/ }));
    expect(push).toHaveBeenCalledWith(expect.stringContaining("zone=CP"), { scroll: false });
  });

  it("clears an active zone and refreshes on demand", async () => {
    const user = userEvent.setup();
    withNavigation(<FilterBar filter={{ ...FILTER, zone: "CP" }} zones={zones} zoneNames={names} loadedAt="09:30" />);
    await user.click(screen.getByRole("button", { name: "Clear zone filter" }));
    expect(push).toHaveBeenCalledWith(expect.not.stringContaining("zone="), { scroll: false });
    await user.click(screen.getByRole("button", { name: "Refresh data" }));
    expect(refresh).toHaveBeenCalledOnce();
  });
});

describe("ZoneBreakdown", () => {
  const zones: ZoneReport[] = [
    { zoneCode: "WP", created: 10, delivered: 8, failed: 2, cancelled: 0, successRate: 0.8 },
    { zoneCode: "UNKNOWN", created: 2, delivered: 1, failed: 0, cancelled: 1, successRate: 1 },
    { zoneCode: "CP", created: 30, delivered: 12, failed: 12, cancelled: 1, successRate: 0.5 },
    { zoneCode: "SP", created: 0, delivered: 0, failed: 0, cancelled: 0, successRate: null },
  ];

  function zoneButtons() {
    return screen.getAllByRole("button", { name: /delivered,/ }).map((b) => b.getAttribute("aria-label")!.split(":")[0]);
  }

  it("ranks active zones by volume, keeps unknown last and can sort by success", async () => {
    withNavigation(<ZoneBreakdown zones={zones} zoneNames={{ WP: "Western", CP: "Central" }} filter={FILTER} />);
    expect(zoneButtons()).toEqual(["Central (CP)", "Western (WP)", "Zone not known yet"]);
    await userEvent.click(screen.getByRole("button", { name: "Success rate" }));
    expect(zoneButtons()).toEqual(["Western (WP)", "Central (CP)", "Zone not known yet"]);
    expect(screen.getByRole("button", { name: /^Zone not known yet/ })).toBeDisabled();
  });

  it("shows a zone's figures on hover and toggles the focus", async () => {
    const { rerender } = withNavigation(<ZoneBreakdown zones={zones} zoneNames={{ CP: "Central" }} filter={FILTER} />);
    const central = screen.getByRole("button", { name: /^Central/ });
    fireEvent.pointerEnter(central);
    expect(screen.getByRole("status")).toHaveTextContent("New parcels30");
    fireEvent.pointerLeave(central);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    await userEvent.click(central);
    expect(push).toHaveBeenLastCalledWith(expect.stringContaining("zone=CP"), { scroll: false });

    rerender(
      <ReportsNavigation>
        <ZoneBreakdown zones={zones} zoneNames={{ CP: "Central" }} filter={{ ...FILTER, zone: "CP" }} />
      </ReportsNavigation>,
    );
    expect(screen.getByText(/all zones, for comparison/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Select to show all zones/ }));
    expect(push).toHaveBeenLastCalledWith(expect.not.stringContaining("zone="), { scroll: false });
  });
});

describe("CourierLeaderboard", () => {
  const couriers: CourierReport[] = Array.from({ length: 10 }, (_, i) => ({
    courierId: `courier-${String(i).padStart(2, "0")}`,
    delivered: 20 - i,
    failed: i,
    successRate: (20 - i) / 20,
  }));

  function names() {
    return screen.getAllByRole("row").slice(1).map((row) => within(row).getByTitle(/courier-/).textContent);
  }

  it("shows the top eight and expands to everyone", async () => {
    render(<CourierLeaderboard couriers={couriers} />);
    expect(names()).toHaveLength(8);
    await userEvent.click(screen.getByRole("button", { name: "Show all 10" }));
    expect(names()).toHaveLength(10);
    await userEvent.click(screen.getByRole("button", { name: "Show top 8" }));
    expect(names()).toHaveLength(8);
  });

  it("sorts by a column and flips the order", async () => {
    render(<CourierLeaderboard couriers={couriers} />);
    await userEvent.click(screen.getByRole("button", { name: /Failed/ }));
    expect(names()[0]).toBe("courier-00");
    await userEvent.click(screen.getByRole("button", { name: /Failed/ }));
    expect(names()[0]).toBe("courier-09");
    await userEvent.click(screen.getByRole("button", { name: /Success/ }));
    expect(screen.getByRole("columnheader", { name: /Success/ })).toHaveAttribute("aria-sort", "descending");
  });

  it("finds a courier and says when nobody matches", async () => {
    render(<CourierLeaderboard couriers={couriers} />);
    await userEvent.type(screen.getByRole("textbox", { name: "Find a courier" }), "09");
    expect(names()).toEqual(["courier-09"]);
    await userEvent.type(screen.getByRole("textbox", { name: "Find a courier" }), "x");
    expect(screen.getByText(/No courier matches/)).toBeInTheDocument();
  });
});

describe("TrendPanel", () => {
  function panel(previousDays: Day[] | null = PREVIOUS_DAYS) {
    return render(
      <TrendPanel days={DAYS} previousDays={previousDays} totals={TOTALS} previousTotals={previousDays ? PREVIOUS : null} fileStem="2026-10-01_2026-10-04" />,
    );
  }

  it("charts the selected metric against the previous period", async () => {
    panel();
    expect(screen.getByRole("img", { name: /Deliveries per day/ })).toBeInTheDocument();
    expect(screen.getByText("Previous period")).toBeInTheDocument();
    expect(screen.getByText("Peak 9")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Success rate" }));
    expect(screen.getByRole("img", { name: /Success rate per day/ })).toBeInTheDocument();
    expect(screen.getByText("Success rate for the period")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("switch"));
    expect(screen.queryByText("Previous period")).not.toBeInTheDocument();
  });

  it("reads each day from the keyboard and the pointer", () => {
    panel();
    const chart = screen.getByRole("img", { name: /per day/ });
    fireEvent.focus(chart);
    expect(screen.getByRole("status")).toHaveTextContent("New parcels8");
    fireEvent.keyDown(chart, { key: "ArrowLeft" });
    expect(screen.getByRole("status")).toHaveTextContent("New parcels5");
    expect(screen.getByRole("status")).toHaveTextContent(/on 29 Sep/);
    for (const key of ["Home", "ArrowRight", "End", "a"]) fireEvent.keyDown(chart, { key });
    fireEvent.keyDown(chart, { key: "Escape" });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    const overlay = chart.querySelector("rect")!;
    overlay.getBoundingClientRect = () => ({ left: 0, width: 300 }) as DOMRect;
    fireEvent.pointerMove(overlay, { clientX: 10 });
    expect(screen.getByRole("status")).toBeInTheDocument();
    fireEvent.pointerLeave(overlay);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    fireEvent.pointerDown(overlay, { clientX: 290 });
    fireEvent.blur(chart);
  });

  it("switches to a table and disables comparison without history", async () => {
    panel(null);
    expect(screen.getByRole("switch")).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Show table" }));
    expect(screen.getAllByRole("row")).toHaveLength(DAYS.length + 1);
    await userEvent.click(screen.getByRole("button", { name: "Show chart" }));
    expect(screen.getByRole("img", { name: /per day/ })).toBeInTheDocument();
  });

  it("exports what is on screen as CSV", async () => {
    const createObjectURL = vi.fn(() => "blob:report");
    const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL, revokeObjectURL }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    panel();
    await userEvent.click(screen.getByRole("button", { name: "CSV" }));
    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:report");
  });
});
