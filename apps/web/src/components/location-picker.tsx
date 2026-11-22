"use client";

import "leaflet/dist/leaflet.css";
import { useState } from "react";
import type { LatLngBoundsExpression } from "leaflet";
import { CircleMarker, MapContainer, TileLayer, useMapEvents } from "react-leaflet";
import { BffError, bffFetch, problemMessage } from "@/lib/bff";
import { locatePath, pricingErrorMessage, roundCoordinate, type GeoPick, type LocatedPoint } from "@/lib/pricing";

/** The island, so the map opens on the whole service area. A viewport, not coverage data. */
const SRI_LANKA_BOUNDS: LatLngBoundsExpression = [
  [5.85, 79.5],
  [9.9, 81.95],
];

interface LocationPickerProps {
  id: string;
  /** What is being picked, e.g. "collection". Used in the hint and the map's accessible name. */
  purpose: string;
  tileUrl: string;
  attribution: string;
  point?: GeoPick;
  /** Called only once the server has said which district the point is in. */
  onPick: (point: GeoPick, located: LocatedPoint) => void;
  /** Custom height CSS class for the map container (defaults to "h-56"). */
  heightClass?: string;
}

type Status = "idle" | "locating" | "failed";

/**
 * A map to pin an exact collection or delivery point.
 *
 * The district is never worked out in the browser: each click asks pricing-service, which
 * checks it against the same boundaries the quote endpoint uses, so the form cannot hold a
 * point and a district that disagree. The district list beside it stays the primary,
 * keyboard-accessible way to fill the form; the map only adds precision.
 */
export function LocationPicker({
  id,
  purpose,
  tileUrl,
  attribution,
  point,
  onPick,
  heightClass = "h-56",
}: LocationPickerProps) {
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState<string>();

  async function locate(picked: GeoPick) {
    const rounded = { latitude: roundCoordinate(picked.latitude), longitude: roundCoordinate(picked.longitude) };
    setStatus("locating");
    setMessage(undefined);
    try {
      const located = await bffFetch<LocatedPoint>(locatePath(rounded), {}, fetch);
      setStatus("idle");
      onPick(rounded, located);
    } catch (error) {
      const code = error instanceof BffError ? error.problem.code : undefined;
      setMessage(pricingErrorMessage(code) ?? problemMessage(error));
      setStatus("failed");
    }
  }

  return (
    <div className="space-y-2">
      <p id={`${id}-hint`} className="text-xs text-slate-600">
        Tap the map at the exact {purpose} point. The district is filled in for you.
      </p>
      <div
        className={`${heightClass} w-full overflow-hidden rounded-md border border-slate-200`}
        role="group"
        aria-label={`Map for the ${purpose} point`}
        aria-describedby={`${id}-hint`}
      >
        <MapContainer bounds={SRI_LANKA_BOUNDS} scrollWheelZoom={false} className="h-full w-full">
          <TileLayer url={tileUrl} attribution={attribution} />
          <ClickToLocate onClick={locate} />
          {point ? (
            <CircleMarker
              center={[point.latitude, point.longitude]}
              radius={8}
              pathOptions={{ className: "map-pin" }}
            />
          ) : null}
        </MapContainer>
      </div>
      {status === "locating" ? (
        <p aria-live="polite" className="text-xs text-slate-600">
          Finding the district...
        </p>
      ) : null}
      {point && status !== "locating" ? (
        <p className="text-xs text-slate-600">
          Pinned at {point.latitude.toFixed(5)}, {point.longitude.toFixed(5)}
        </p>
      ) : null}
      {message ? (
        <p role="alert" className="field-error">
          {message}
        </p>
      ) : null}
    </div>
  );
}

function ClickToLocate({ onClick }: { onClick: (point: GeoPick) => void }) {
  useMapEvents({
    click(event) {
      onClick({ latitude: event.latlng.lat, longitude: event.latlng.lng });
    },
  });
  return null;
}
