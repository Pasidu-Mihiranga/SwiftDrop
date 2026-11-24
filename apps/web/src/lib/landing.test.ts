import { describe, expect, it } from "vitest";
import { SOCIAL_MARKS, TECH_MARKS } from "./brand-marks";
import { FAQS, FEATURES, FOOTER_COLUMNS, HERO_FACTS, PLANS, SAMPLE_QUOTES, STEPS } from "./landing";

describe("landing page content", () => {
  it("has the sections' required counts", () => {
    expect(HERO_FACTS).toHaveLength(4);
    expect(TECH_MARKS).toHaveLength(6);
    expect(FEATURES).toHaveLength(3);
    expect(STEPS).toHaveLength(3);
    expect(SAMPLE_QUOTES).toHaveLength(3);
    expect(PLANS).toHaveLength(3);
    expect(FAQS).toHaveLength(6);
    expect(FOOTER_COLUMNS).toHaveLength(3);
    expect(SOCIAL_MARKS.map((mark) => mark.name)).toEqual(["Facebook", "X", "WhatsApp"]);
  });

  it("marks exactly one plan as recommended", () => {
    expect(PLANS.filter((plan) => plan.recommended).map((plan) => plan.name)).toEqual(["Online stores"]);
  });

  it("labels every testimonial as a sample, never as a named person", () => {
    for (const quote of SAMPLE_QUOTES) expect(quote.source).toBe("Sample quote");
  });

  it("states only facts the service is configured with", () => {
    expect(HERO_FACTS.map((fact) => fact.value)).toEqual(["25", "15 min", "3", "LKR 350"]);
    expect(PLANS[0].price).toBe("LKR 350");
  });

  it("gives every mark a drawable path and every feature an image with alt text", () => {
    for (const mark of [...TECH_MARKS, ...SOCIAL_MARKS]) expect(mark.path).toMatch(/^[Mm]/);
    for (const feature of FEATURES) {
      expect(feature.image.src).toMatch(/^\/landing\/.+\.avif$/);
      expect(feature.image.alt.length).toBeGreaterThan(10);
    }
  });

  it("links only to app routes, anchors or a mail address", () => {
    const hrefs = [
      ...FEATURES.map((feature) => feature.link.href),
      ...PLANS.map((plan) => plan.action.href),
      ...FOOTER_COLUMNS.flatMap((column) => column.links.map((link) => link.href)),
    ];
    for (const href of hrefs) expect(href).toMatch(/^(\/|mailto:)/);
  });
});
