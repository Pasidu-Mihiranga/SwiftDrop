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

  it("offers address search only when the deployment turns it on", () => {
    const tiles = "https://tiles.example/{z}/{x}/{y}.png";

    expect(mapConfig({ MAP_TILE_URL: tiles })?.addressSearch).toBeUndefined();
    expect(mapConfig({ MAP_TILE_URL: tiles, ADDRESS_SEARCH_ENABLED: "" })?.addressSearch).toBeUndefined();
    expect(mapConfig({ MAP_TILE_URL: tiles, ADDRESS_SEARCH_ENABLED: "false" })?.addressSearch).toBeUndefined();
    expect(mapConfig({ MAP_TILE_URL: tiles, ADDRESS_SEARCH_ENABLED: " TRUE " })?.addressSearch?.attribution).toContain(
      "OpenStreetMap",
    );
    expect(
      mapConfig({ MAP_TILE_URL: tiles, ADDRESS_SEARCH_ENABLED: "true", ADDRESS_SEARCH_ATTRIBUTION: "Search by Example" })
        ?.addressSearch,
    ).toEqual({ attribution: "Search by Example" });
    // Search lives on the map, so without tiles there is nothing to show it on.
    expect(mapConfig({ ADDRESS_SEARCH_ENABLED: "true" })).toBeUndefined();
  });
});
