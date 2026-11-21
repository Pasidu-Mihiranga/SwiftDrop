export interface DispatchParcel {
  parcelId: string; trackingNumber: string; zone: string; sizeClass: string;
  pickupCity: string; dropoffCity: string; createdAt: string; status: string;
  assignmentId: string | null; courierId: string | null; runDate: string | null;
}
export interface DispatchPage { content: DispatchParcel[]; totalElements: number; page: number; size: number }
export interface Workload {
  courierId: string; courierName: string; homeZoneId: string | null; capacity: number | null;
  assignable: boolean; activeCount: number; deliveredToday: number; failedToday: number;
}
export interface DispatchZone { id: string; code: string; name: string }
export function businessDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Colombo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  return ["year", "month", "day"].map(key => parts.find(p => p.type === key)?.value).join("-");
}
export function parcelAge(createdAt: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - Date.parse(createdAt)) / 60000));
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / 1440)}d`;
}
export function zoneMatches(courier: Workload, parcel: DispatchParcel, zones: DispatchZone[]): boolean {
  return zones.some(z => z.id === courier.homeZoneId && z.code === parcel.zone);
}
export function rankedCouriers(couriers: Workload[], parcel: DispatchParcel, zones: DispatchZone[]): Workload[] {
  return couriers.filter(c => c.assignable && c.courierId !== parcel.courierId).sort((a, b) =>
    Number(zoneMatches(b, parcel, zones)) - Number(zoneMatches(a, parcel, zones)) || a.activeCount - b.activeCount || a.courierName.localeCompare(b.courierName));
}
export async function dispatchRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/bff/api/v1/${path}`, { cache: "no-store", ...init });
  if (!res.ok) {
    const problem = await res.json().catch(() => ({})) as { detail?: string };
    throw new Error(problem.detail ?? `Dispatch request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}
