/**
 * Format an ISO timestamp for display.
 * Example: "12 Apr · 09:30"
 */
export function formatDate(ts: string | number | Date): string {
  try {
    return new Date(ts).toLocaleString('en-GB', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return String(ts);
  }
}

/**
 * Format a confidence score (0–1) as a percentage string.
 * Example: 0.82 → "82%"
 */
export function formatConfidence(score: number | null | undefined): string {
  if (score == null) return '—';
  return `${Math.round(score * 100)}%`;
}

/**
 * Format a lat/lng pair for display.
 * Example: formatCoords(25.2048, 55.2708) → "25.20480, 55.27080"
 */
export function formatCoords(lat: number, lng: number): string {
  return `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
}

/**
 * Map a backend category key to a human-readable label.
 */
export const CATEGORY_LABELS: Record<string, string> = {
  STEP_FREE_ISSUE: 'Step-free Issue',
  SURFACE_HAZARD: 'Surface Hazard',
  OBSTRUCTION: 'Obstruction',
  LIGHTING_ISSUE: 'Lighting Issue',
  HEAT_NO_SHADE: 'Heat / No Shade',
};

/**
 * Map a backend severity key to a human-readable label.
 */
export const SEVERITY_LABELS: Record<string, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
};
