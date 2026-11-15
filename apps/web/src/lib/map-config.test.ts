import { describe, expect, it } from "vitest";
import { mapConfig } from "./map-config";

describe("mapConfig", () => {
  it("is off when no tile server is configured", () => {
    expect(mapConfig({})).toBeUndefined();
    expect(mapConfig({ MAP_TILE_URL: "   " })).toBeUndefined();
  });

  it("ignores a value that is not a tile URL template", () => {
    expect(mapConfig({ MAP_TILE_URL: "https://tiles.example/map.png" })).toBeUndefined();
    expect(mapConfig({ MAP_TILE_URL: "ftp://tiles.example/{z}/{x}/{y}.png" })).toBeUndefined();
  });

  it("returns the template and the provider's attribution", () => {
    expect(
      mapConfig({ MAP_TILE_URL: "https://tiles.example/{z}/{x}/{y}.png", MAP_TILE_ATTRIBUTION: "© Example" }),
    ).toEqual({ tileUrl: "https://tiles.example/{z}/{x}/{y}.png", attribution: "© Example" });
  });

  it("falls back to the OpenStreetMap credit when no attribution is given", () => {
    expect(mapConfig({ MAP_TILE_URL: "https://tiles.example/{z}/{x}/{y}.png" })?.attribution).toContain(
      "OpenStreetMap",
    );
  });
});
