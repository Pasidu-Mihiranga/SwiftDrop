"use client";

import { type KeyboardEvent, type RefObject, useEffect, useId, useMemo, useRef, useState } from "react";
import {
  MAX_DAYS,
  PRESETS,
  type PresetKey,
  type ReportFilter,
  formatCount,
  formatRange,
  isValidRange,
  reportsHref,
  zoneLabel,
} from "@/lib/reports";
import { Icon } from "./icons";
import { useReportsNavigation } from "./reports-navigation";

const SHORT: Record<PresetKey, string> = { "7d": "7D", "30d": "30D", "90d": "90D", mtd: "MTD" };

interface Props {
  filter: ReportFilter;
  /** Zones to offer, with their activity in the period when known. */
  zones: { code: string; activity: number | null }[];
  zoneNames: Record<string, string>;
  /** When the data on screen was loaded (HH:mm, Sri Lanka time). */
  loadedAt: string;
}

/**
 * The one filter row that scopes the whole page. Sticky, so it stays at hand while scrolling.
 * Every change is a URL change (shareable, back-button friendly) applied without a full reload.
 */
export function FilterBar({ filter, zones, zoneNames, loadedAt }: Props) {
  const { navigate, refresh, pending } = useReportsNavigation();

  return (
    <div className="sticky top-0 z-30 -mx-4 border-b border-slate-200/80 bg-slate-50/85 px-4 py-3 backdrop-blur supports-[backdrop-filter]:bg-slate-50/70 sm:-mx-6 sm:px-6">
      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Period" className="inline-flex rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {(Object.keys(PRESETS) as PresetKey[]).map((preset) => {
            const selected = filter.preset === preset;
            return (
              <button
                key={preset}
                type="button"
                title={PRESETS[preset]}
                aria-pressed={selected}
                onClick={() => !selected && navigate(reportsHref({ preset, zone: filter.zone }))}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                  selected ? "bg-brand-600 text-white shadow-sm" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`}
              >
                <span className="sr-only">{PRESETS[preset]}</span>
                <span aria-hidden="true">{SHORT[preset]}</span>
              </button>
            );
          })}
          <CustomRange filter={filter} onApply={(from, to) => navigate(reportsHref({ preset: "custom", from, to, zone: filter.zone }))} />
        </div>

        <ZonePicker
          zone={filter.zone}
          zones={zones}
          zoneNames={zoneNames}
          onChange={(zone) => navigate(reportsHref({ ...filter, zone }))}
        />

        <div className="ml-auto flex items-center gap-2 text-xs text-slate-500">
          <span className="hidden sm:inline">{pending ? "Updating..." : `Updated ${loadedAt}`}</span>
          <button
            type="button"
            onClick={refresh}
            disabled={pending}
            aria-label="Refresh data"
            className="rounded-lg p-2 text-slate-500 ring-1 ring-slate-200 transition hover:bg-white hover:text-slate-900 disabled:opacity-50"
          >
            <svg aria-hidden="true" viewBox="0 0 16 16" fill="none" className={`h-4 w-4 ${pending ? "animate-spin" : ""}`}>
              <path d="M13 8a5 5 0 1 1-1.5-3.5M13 2.5v3h-3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}

function CustomRange({ filter, onApply }: { filter: ReportFilter; onApply: (from: string, to: string) => void }) {
  const [open, setOpen] = useState(false);
  const [from, setFrom] = useState(filter.from);
  const [to, setTo] = useState(filter.to);
  const root = useRef<HTMLDivElement>(null);
  const id = useId();
  useDismiss(root, open, () => setOpen(false));
  useEffect(() => {
    setFrom(filter.from);
    setTo(filter.to);
  }, [filter.from, filter.to]);

  const valid = isValidRange(from, to);
  const selected = filter.preset === "custom";

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
        className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition ${
          selected ? "bg-brand-600 text-white shadow-sm" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
        }`}
      >
        <Icon name="calendar" />
        {selected ? formatRange(filter.from, filter.to) : "Custom"}
      </button>
      {open && (
        <div id={id} role="dialog" aria-label="Custom date range" className="viz-fade absolute left-0 top-full z-40 mt-2 w-72 rounded-xl bg-white p-4 shadow-xl ring-1 ring-slate-200">
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (!valid) return;
              setOpen(false);
              onApply(from, to);
            }}
            className="space-y-3"
          >
            <p className="text-sm font-semibold text-slate-900">Custom range</p>
            <div className="grid grid-cols-2 gap-2">
              <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
                From
                <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="input px-2 py-1.5" autoFocus />
              </label>
              <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
                To
                <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className="input px-2 py-1.5" />
              </label>
            </div>
            <p className={`text-xs ${valid ? "text-slate-500" : "text-[var(--viz-bad)]"}`} role={valid ? undefined : "alert"}>
              {valid ? "Days follow Sri Lanka time." : `Pick a start on or before the end, at most ${MAX_DAYS} days apart.`}
            </p>
            <div className="flex justify-end gap-2 border-t border-slate-100 pt-3">
              <button type="button" onClick={() => setOpen(false)} className="btn-secondary px-3 py-1.5">Cancel</button>
              <button type="submit" disabled={!valid} className="btn-primary px-3 py-1.5">Apply</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

function ZonePicker({
  zone,
  zones,
  zoneNames,
  onChange,
}: {
  zone?: string;
  zones: Props["zones"];
  zoneNames: Record<string, string>;
  onChange: (zone?: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const root = useRef<HTMLDivElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const id = useId();
  useDismiss(root, open, () => setOpen(false));

  const options = useMemo(() => {
    const q = query.trim().toLowerCase();
    return zones.filter(({ code }) => !q || zoneLabel(code, zoneNames).toLowerCase().includes(q));
  }, [query, zones, zoneNames]);

  function choose(code?: string) {
    setOpen(false);
    setQuery("");
    if (code !== zone) onChange(code);
  }

  function moveFocus(event: KeyboardEvent, from: number) {
    const buttons = Array.from(list.current?.querySelectorAll("button") ?? []);
    if (event.key === "ArrowDown") buttons[Math.min(buttons.length - 1, from + 1)]?.focus();
    else if (event.key === "ArrowUp") (from <= 0 ? root.current?.querySelector("input") : buttons[from - 1])?.focus();
    else return;
    event.preventDefault();
  }

  return (
    <div ref={root} className="relative">
      <div
        className={`inline-flex items-center rounded-xl bg-white shadow-sm ring-1 transition ${
          zone ? "ring-brand-500" : "ring-slate-200"
        }`}
      >
        <button
          type="button"
          aria-expanded={open}
          aria-controls={id}
          onClick={() => setOpen((o) => !o)}
          className="inline-flex items-center gap-2 rounded-xl py-2 pl-3 pr-2 text-sm"
        >
          <Icon name="pin" className="h-4 w-4 text-slate-400" />
          <span className="text-slate-500">Zone</span>
          <span className="max-w-40 truncate font-medium text-slate-900">{zone ? zoneLabel(zone, zoneNames) : "All zones"}</span>
          <Icon name="chevron" className="h-4 w-4 text-slate-400" />
        </button>
        {zone && (
          <button
            type="button"
            onClick={() => choose(undefined)}
            aria-label="Clear zone filter"
            className="mr-1 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
          >
            <svg aria-hidden="true" viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none">
              <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </button>
        )}
      </div>

      {open && (
        <div id={id} className="viz-fade absolute left-0 top-full z-40 mt-2 w-72 overflow-hidden rounded-xl bg-white shadow-xl ring-1 ring-slate-200">
          <div className="flex items-center gap-2 border-b border-slate-100 px-3">
            <Icon name="search" className="h-4 w-4 text-slate-400" />
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => moveFocus(e, -1)}
              placeholder="Search zones"
              aria-label="Search zones"
              className="w-full py-2.5 text-sm outline-none placeholder:text-slate-400"
            />
          </div>
          <ul ref={list} className="max-h-72 overflow-auto py-1" role="listbox" aria-label="Zones">
            {[{ code: undefined as string | undefined, activity: null as number | null }, ...options].map((option, index) => {
              const selected = option.code === zone;
              return (
                <li key={option.code ?? "all"} role="option" aria-selected={selected}>
                  <button
                    type="button"
                    onClick={() => choose(option.code)}
                    onKeyDown={(e) => moveFocus(e, index)}
                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-slate-50 focus:bg-slate-50 focus:outline-none"
                  >
                    <span className="flex h-4 w-4 items-center justify-center text-brand-600">
                      {selected && <Icon name="check" className="h-4 w-4" />}
                    </span>
                    <span className={`flex-1 truncate ${selected ? "font-semibold text-slate-900" : "text-slate-700"}`}>
                      {option.code ? zoneLabel(option.code, zoneNames) : "All zones"}
                    </span>
                    {option.activity !== null && (
                      <span className="text-xs tabular-nums text-slate-400">{formatCount(option.activity)}</span>
                    )}
                  </button>
                </li>
              );
            })}
            {options.length === 0 && <li className="px-3 py-3 text-sm text-slate-500">No zone matches &quot;{query}&quot;.</li>}
          </ul>
        </div>
      )}
    </div>
  );
}

/** Closes a popover on Escape or on a pointer press outside it. */
function useDismiss(root: RefObject<HTMLElement | null>, open: boolean, close: () => void) {
  useEffect(() => {
    if (!open) return;
    const onPointer = (event: PointerEvent) => {
      if (root.current && !root.current.contains(event.target as Node)) close();
    };
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [root, open, close]);
}
