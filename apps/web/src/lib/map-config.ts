type Env = Record<string, string | undefined>;

/** Where the quote page's map draws its tiles from. */
export interface MapConfig {
  /** A Leaflet tile URL template, e.g. https://tile.example/{z}/{x}/{y}.png */
  tileUrl: string;
  /** The attribution the tile provider requires, shown on the map. */
  attribution: string;
}

/**
 * Map settings from the environment, or undefined when no tile server is configured, in which
 * case the quote form simply offers the district lists without a map. Read on the server and
 * passed down, so no tile host is compiled into the bundle.
 */
export function mapConfig(env: Env = process.env): MapConfig | undefined {
  const tileUrl = env.MAP_TILE_URL?.trim();
  if (!tileUrl) return undefined;
  if (!/^https?:\/\//.test(tileUrl) || !tileUrl.includes("{z}") || !tileUrl.includes("{x}") || !tileUrl.includes("{y}")) {
    return undefined;
  }
  const attribution = env.MAP_TILE_ATTRIBUTION?.trim() || "Map data © OpenStreetMap contributors";
  return { tileUrl, attribution };
}
