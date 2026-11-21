import type { Timeline } from "./domain/timeline";

/** Port: told after a timeline changed, so live clients can be notified (Socket.IO adapter in F5). */
export interface TimelineBroadcaster {
  timelineChanged(timeline: Timeline, eventType: string): Promise<void>;
}

export const TIMELINE_BROADCASTER = Symbol("TIMELINE_BROADCASTER");
