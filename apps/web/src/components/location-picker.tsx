"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useState } from "react";
import type { LatLngBoundsExpression } from "leaflet";
import { CircleMarker, MapContainer, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { searchAddresses, suggestionToPick, type AddressSuggestion } from "@/lib/address-search";
import { BffError, bffFetch, problemMessage } from "@/lib/bff";
import { locatePath, pricingErrorMessage, roundCoordinate, type GeoPick, type LocatedPoint } from "@/lib/pricing";

/** The island, so the map opens on the whole service area. A viewport, not coverage data. */
const SRI_LANKA_BOUNDS: LatLngBoundsExpression = [
  [5.85, 79.5],
  [9.9, 81.95],
];

/** Close enough to see the street a searched address is on. */
const SEARCH_RESULT_ZOOM = 16;

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
  /** Present when the deployment has an address search; carries the attribution it requires. */
  search?: { attribution: string };
}

type Status = "idle" | "locating" | "failed";

/**
 * A map to pin an exact collection or delivery point.
 *
 * The district is never worked out in the browser: each click asks pricing-service, which
 * checks it against the same boundaries the quote endpoint uses, so the form cannot hold a
 * point and a district that disagree. The district list beside it stays the primary,
 * keyboard-accessible way to fill the form; the map only adds precision. Where the deployment
 * has a geocoder, an address can be searched instead of found by eye; the server checks each
 * result against the same boundaries before offering it.
 */
export function LocationPicker({
  id,
  purpose,
  tileUrl,
  attribution,
  point,
  onPick,
  heightClass = "h-56",
  search,
}: LocationPickerProps) {
  const [status, setStatus] = useState<Status>("idle");
  const [message, setMessage] = useState<string>();
  const [focus, setFocus] = useState<GeoPick>();

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

  function chooseSuggestion(suggestion: AddressSuggestion) {
    const { point: chosen, located } = suggestionToPick(suggestion);
    const rounded = { latitude: roundCoordinate(chosen.latitude), longitude: roundCoordinate(chosen.longitude) };
    setMessage(undefined);
    setStatus("idle");
    setFocus(rounded);
    onPick(rounded, located);
  }

  return (
    <div className="space-y-2">
      {search ? <AddressSearch id={id} purpose={purpose} attribution={search.attribution} onChoose={chooseSuggestion} /> : null}
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
          <FlyTo point={focus} />
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

/**
 * Searches on Enter or the button, never as the customer types. It sits inside the quote and
 * booking forms, so it is not a form of its own and Enter must not submit the outer one.
 */
function AddressSearch({
  id,
  purpose,
  attribution,
  onChoose,
}: {
  id: string;
  purpose: string;
  attribution: string;
  onChoose: (suggestion: AddressSuggestion) => void;
}) {
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([]);
  const [note, setNote] = useState<string>();
  const [unavailable, setUnavailable] = useState(false);

  async function run() {
    if (busy) return;
    setBusy(true);
    setNote(undefined);
    const result = await searchAddresses(query);
    setBusy(false);
    if (result.ok) {
      setSuggestions(result.suggestions);
      setNote(result.message);
    } else {
      setSuggestions([]);
      setNote(result.message);
      setUnavailable(Boolean(result.unavailable));
    }
  }

  if (unavailable) {
    return <p className="text-xs text-slate-600">{note}</p>;
  }

  return (
    <div className="space-y-1">
      <label htmlFor={`${id}-search`} className="block text-xs font-medium text-slate-700">
        Search for the {purpose} address
      </label>
      <div className="flex gap-2">
        <input
          id={`${id}-search`}
          type="search"
          className="input"
          value={query}
          maxLength={120}
          autoComplete="street-address"
          placeholder="e.g. 45 Galle Road, Colombo"
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              void run();
            }
          }}
        />
        <button type="button" className="btn-secondary shrink-0 px-3 py-2 text-xs" onClick={() => void run()} disabled={busy}>
          {busy ? "Searching..." : "Search"}
        </button>
      </div>
      {suggestions.length > 0 ? (
        <ul className="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white text-sm" aria-label="Matching places">
          {suggestions.map((suggestion) => (
            <li key={`${suggestion.latitude},${suggestion.longitude},${suggestion.label}`}>
              <button
                type="button"
                className="w-full px-3 py-2 text-left hover:bg-slate-50"
                onClick={() => {
                  setSuggestions([]);
                  setNote(undefined);
                  onChoose(suggestion);
                }}
              >
                <span className="block">{suggestion.label}</span>
                <span className="block text-xs text-slate-500">{suggestion.district} district</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      {note ? (
        <p aria-live="polite" className="text-xs text-slate-600">
          {note}
        </p>
      ) : null}
      <p className="text-[11px] text-slate-500">{attribution}</p>
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

/** Moves the map to a searched place; a tap on the map leaves the view where the customer put it. */
function FlyTo({ point }: { point?: GeoPick }) {
  const map = useMap();
  useEffect(() => {
    if (point) map.flyTo([point.latitude, point.longitude], Math.max(map.getZoom(), SEARCH_RESULT_ZOOM));
  }, [map, point]);
  return null;
}
