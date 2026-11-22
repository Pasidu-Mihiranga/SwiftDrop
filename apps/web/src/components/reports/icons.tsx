/** Small line icons for the reports dashboard (16px, currentColor, decorative). */
type IconName =
  | "calendar"
  | "chevron"
  | "check"
  | "pin"
  | "search"
  | "download"
  | "up"
  | "down"
  | "flat"
  | "peak"
  | "risk"
  | "best"
  | "courier"
  | "info"
  | "chart"
  | "table"
  | "alert"
  | "empty"
  | "sort";

const PATHS: Record<IconName, string> = {
  calendar: "M3 5.5h10M5 2.5v2M11 2.5v2M3.5 4h9a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
  chevron: "M4.5 6.5 8 10l3.5-3.5",
  check: "M3.5 8.5l3 3 6-7",
  pin: "M8 14s4.5-4.2 4.5-7.5a4.5 4.5 0 1 0-9 0C3.5 9.8 8 14 8 14zM8 8.2a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4z",
  search: "M7.2 12.4a5.2 5.2 0 1 0 0-10.4 5.2 5.2 0 0 0 0 10.4zM14 14l-3-3",
  download: "M8 2.5v8M4.5 7 8 10.5 11.5 7M3 13.5h10",
  up: "M8 12.5v-9M4.5 7 8 3.5 11.5 7",
  down: "M8 3.5v9M4.5 9 8 12.5 11.5 9",
  flat: "M3.5 8h9",
  peak: "M2.5 12.5 6 7.5l2.5 2.5 5-6.5M10 3.5h3.5V7",
  risk: "M8 2.5 14 13H2L8 2.5zM8 6.5v3M8 11.4v.1",
  best: "M8 2.5l1.7 3.5 3.8.5-2.8 2.7.7 3.8L8 11.2 4.6 13l.7-3.8L2.5 6.5l3.8-.5L8 2.5z",
  courier: "M8 7.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM3 13.5c.6-2.6 2.6-4 5-4s4.4 1.4 5 4",
  info: "M8 14A6 6 0 1 0 8 2a6 6 0 0 0 0 12zM8 7.5v3.5M8 5.2v.1",
  chart: "M2.5 13.5h11M4 11l3-4 2.5 2 3.5-5",
  table: "M2.5 3.5h11v9h-11zM2.5 6.5h11M2.5 9.5h11M6.5 3.5v9",
  alert: "M8 14A6 6 0 1 0 8 2a6 6 0 0 0 0 12zM8 5v3.5M8 10.8v.1",
  empty: "M2.5 9.5 4.5 4h7l2 5.5M2.5 9.5v3h11v-3M2.5 9.5h3.2l.8 1.5h3l.8-1.5h3.2",
  sort: "M5 3v10M2.5 10.5 5 13l2.5-2.5M11 13V3M8.5 5.5 11 3l2.5 2.5",
};

export function Icon({ name, className = "h-4 w-4" }: { name: IconName; className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 16 16" fill="none" className={className}>
      <path d={PATHS[name]} stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
