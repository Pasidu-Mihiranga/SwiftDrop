import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { RateMatrixGrid } from "./rate-matrix-grid";
import { buildRateMatrix, type RateCard } from "@/lib/pricing";

const zones = ["WP", "CP"].map((code) => ({ code, name: code, districts: [], active: true }));
const card: RateCard = {
  id: "1",
  fromZone: "WP",
  toZone: "CP",
  baseFee: 475,
  perKgFee: 78,
  expressMultiplier: 1.5,
  codFeePct: 0.015,
  effectiveFrom: "2026-01-01",
};
const grid = (cards: RateCard[]) => render(<RateMatrixGrid matrix={buildRateMatrix(zones, cards, "2026-06-01")} />);

describe("RateMatrixGrid", () => {
  it("links a priced lane to its own history and form", () => {
    grid([card]);

    expect(screen.getByRole("link", { name: "Change the rate for WP to CP" })).toHaveAttribute(
      "href",
      "/admin/pricing/rate-cards?fromZone=WP&toZone=CP#publish",
    );
    expect(screen.getByText("475")).toBeInTheDocument();
    expect(screen.getByText("+78/kg")).toBeInTheDocument();
  });

  it("says when weight is charged in bands rather than quoting one rate", () => {
    grid([{ ...card, tiers: [{ upToKg: 5, perKgFee: 80 }, { upToKg: null, perKgFee: 60 }] }]);

    expect(screen.getByText("bands")).toBeInTheDocument();
  });

  it("marks a lane with no rate and offers to add one", () => {
    grid([card]);

    expect(screen.getByRole("link", { name: "Add a rate for CP to WP" })).toHaveTextContent("None");
  });

  it("explains an empty zone map", () => {
    render(<RateMatrixGrid matrix={buildRateMatrix([], [], "2026-06-01")} />);

    expect(screen.getByText(/No active zones/)).toBeInTheDocument();
  });
});
