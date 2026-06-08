import { Marker, Popup } from "react-leaflet";
import L from "leaflet";
import type { Report, ReportCategory } from "../../types/report";

interface ReportMarkerProps {
  report: Report;
}

// Colors are muted (not neon) — WCAG AA on dark backgrounds.
// Each category also has a unique emoji icon so they're
// distinguishable without relying on color alone (F-6 / US-7).
const categoryConfig: Record<
  ReportCategory,
  { color: string; glow: string; icon: string; label: string; description: string }
> = {
  STEP_FREE_ISSUE: {
    color: "#f87171",   // muted red
    glow:  "rgba(248,113,113,0.3)",
    icon:  "♿",
    label: "Step-free Issue",
    description: "Stairs, steep ramp, or missing curb cut",
  },
  SURFACE_HAZARD: {
    color: "#fb923c",   // muted amber
    glow:  "rgba(251,146,60,0.3)",
    icon:  "⚠",
    label: "Surface Hazard",
    description: "Broken pavement, loose surface, or wet floor",
  },
  OBSTRUCTION: {
    color: "#e879f9",   // muted purple
    glow:  "rgba(232,121,249,0.3)",
    icon:  "🚧",
    label: "Obstruction",
    description: "Construction, parked vehicle, or blocked path",
  },
  LIGHTING_ISSUE: {
    color: "#a78bfa",   // muted violet
    glow:  "rgba(167,139,250,0.3)",
    icon:  "💡",
    label: "Lighting Issue",
    description: "Broken or absent street lighting",
  },
  HEAT_NO_SHADE: {
    color: "#fbbf24",   // muted yellow
    glow:  "rgba(251,191,36,0.3)",
    icon:  "☀",
    label: "Heat / No Shade",
    description: "Exposed area with no shade or extreme heat",
  },
};

function createIcon(category: ReportCategory): L.DivIcon {
  const { color, glow, icon } = categoryConfig[category];

  const html = `
    <div class="report-marker-pin" style="--marker-color:${color};--marker-glow:${glow};"
         role="img" aria-label="${categoryConfig[category].label}">
      <div class="report-marker-ring" aria-hidden="true"></div>
      <div class="report-marker-dot" aria-hidden="true">
        <span class="report-marker-icon">${icon}</span>
      </div>
    </div>
  `;

  return L.divIcon({
    html,
    className:   "",
    iconSize:    [38, 38],
    iconAnchor:  [19, 19],
    popupAnchor: [0, -24],
  });
}

function formatDate(ts: string | number | Date): string {
  try {
    return new Date(ts).toLocaleString("en-GB", {
      day:    "2-digit",
      month:  "short",
      hour:   "2-digit",
      minute: "2-digit",
    });
  } catch {
    return String(ts);
  }
}

export default function ReportMarker({ report }: ReportMarkerProps) {
  const cfg = categoryConfig[report.category];

  // Build accessible popup HTML
  const timestampHtml = report.createdAt
    ? `<span>🕐 ${formatDate(report.createdAt)}</span>`
    : "";

  const statusLabel = report.status ?? "Pending";

  const popupHtml = `
    <div class="report-popup" role="article"
         aria-label="${cfg.label} report">
      <div class="report-popup-header">
        <span class="report-popup-icon" aria-hidden="true">${cfg.icon}</span>
        <strong class="report-popup-title">${cfg.label}</strong>
      </div>
      ${report.description
        ? `<p class="report-popup-desc">${report.description}</p>`
        : `<p class="report-popup-desc" style="font-style:italic;opacity:0.5">
             No description provided
           </p>`}
      <div class="report-popup-meta">
        ${timestampHtml}
        <span class="report-popup-status"
              aria-label="Status: ${statusLabel}">
          ${statusLabel}
        </span>
      </div>
    </div>
  `;

  return (
    <Marker
      position={[report.latitude, report.longitude]}
      icon={createIcon(report.category)}
      // Screen-reader title for the marker itself
      title={`${cfg.label}${report.description ? ": " + report.description : ""}`}
    >
      <Popup>
        <div dangerouslySetInnerHTML={{ __html: popupHtml }} />
      </Popup>
    </Marker>
  );
}