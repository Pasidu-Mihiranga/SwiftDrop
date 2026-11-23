import { BffError, bffFetch } from "@/lib/bff";
import type { GeoPick, LocatedPoint } from "@/lib/pricing";

/** A place pricing-service found for a typed address, already inside the service area. */
export interface AddressSuggestion {
  label: string;
  latitude: number;
  longitude: number;
  district: string;
  zone: string;
}

export const MIN_ADDRESS_QUERY = 3;
export const MAX_ADDRESS_QUERY = 120;

export type AddressSearchResult =
  | { ok: true; suggestions: AddressSuggestion[]; message?: string }
  | { ok: false; message: string; unavailable?: boolean };

/** The query as it will be sent: trimmed, single-spaced, or undefined when it is too short or long. */
export function normaliseAddressQuery(text: string): string | undefined {
  const query = text.trim().replace(/\s+/g, " ");
  if (query.length < MIN_ADDRESS_QUERY || query.length > MAX_ADDRESS_QUERY) return undefined;
  return query;
}

export function addressSearchPath(query: string): string {
  return `api/v1/pricing/address-search?${new URLSearchParams({ q: query }).toString()}`;
}

/**
 * Runs one search when the customer asks for it (never as they type: public geocoders forbid
 * autocomplete). Failures come back as a sentence to show, and `unavailable` says the map is
 * still the way to pick a point.
 */
export async function searchAddresses(text: string, fetchImpl: typeof fetch = fetch): Promise<AddressSearchResult> {
  const query = normaliseAddressQuery(text);
  if (!query) {
    return { ok: false, message: `Type at least ${MIN_ADDRESS_QUERY} characters of the address.` };
  }
  try {
    const suggestions = await bffFetch<AddressSuggestion[]>(addressSearchPath(query), {}, fetchImpl);
    return suggestions.length > 0
      ? { ok: true, suggestions }
      : { ok: true, suggestions, message: "No matching place in our service area. Try adding the town, or tap the map." };
  } catch (error) {
    if (error instanceof BffError && error.problem.code === "ADDRESS_SEARCH_UNAVAILABLE") {
      return { ok: false, unavailable: true, message: "Address search is unavailable just now. Tap the map instead." };
    }
    if (error instanceof BffError && error.problem.code === "INVALID_ADDRESS_QUERY") {
      return { ok: false, message: `Type between ${MIN_ADDRESS_QUERY} and ${MAX_ADDRESS_QUERY} characters of the address.` };
    }
    return { ok: false, message: "We could not search just now. Please try again, or tap the map." };
  }
}

/** What the map picker reports for a chosen suggestion, the same shape a tap on the map gives. */
export function suggestionToPick(suggestion: AddressSuggestion): { point: GeoPick; located: LocatedPoint } {
  return {
    point: { latitude: suggestion.latitude, longitude: suggestion.longitude },
    located: { district: suggestion.district, zoneCode: suggestion.zone },
  };
}
