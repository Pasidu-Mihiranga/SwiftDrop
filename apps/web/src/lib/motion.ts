/**
 * Feedback-only motion, shared by every form. Results (saved, errors) fade in with a 4 px rise
 * over 200 ms on a strong ease-out; reduced-motion users get the fade without movement. Nothing
 * animates on page load.
 */
export const FEEDBACK_MOTION =
  "transition-[opacity,translate] duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] starting:opacity-0 starting:translate-y-1 motion-reduce:starting:translate-y-0";

/** Slight press on anything clickable, so the click is felt; removed for reduced motion. */
export const PRESS = "active:scale-[0.97] motion-reduce:active:scale-100";
