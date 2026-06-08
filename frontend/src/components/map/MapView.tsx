import { useState } from "react";
import { MapContainer, TileLayer, ZoomControl, useMap } from "react-leaflet";
import type { Report } from "../../types/report";
import ReportMarker from "./ReportMarker";
import RouteOverlay from "./RouteOverlay";
import MapLegend from "./MapLegend";
import "leaflet/dist/leaflet.css";
import "./MapView.css";

interface MapViewProps {
  reports: Report[];
}

// Puts the recenter button inside the Leaflet context (needs useMap)
function RecenterControl() {
  const map = useMap();
  return (
    <button
      className="map-recenter-btn"
      onClick={() => map.setView([25.2048, 55.2708], 15)}
      aria-label="Re-center map on current area"
      title="Re-center map"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="3" />
        <line x1="12" y1="2"  x2="12" y2="6"  />
        <line x1="12" y1="18" x2="12" y2="22" />
        <line x1="2"  y1="12" x2="6"  y2="12" />
        <line x1="18" y1="12" x2="22" y2="12" />
      </svg>
    </button>
  );
}

export default function MapView({ reports }: MapViewProps) {
  const [showLegend, setShowLegend] = useState(true);

  // Tally reports per category for the legend
  const categoryCounts = reports.reduce<Record<string, number>>((acc, r) => {
    acc[r.category] = (acc[r.category] ?? 0) + 1;
    return acc;
  }, {});

  return (
    <div className="map-wrapper" role="region" aria-label="Accessibility reports map">
      {/* Skip link for keyboard-only users (F-21) */}
      <a href="#report-list" className="map-skip-link">
        Skip map — jump to report list
      </a>

      {/* HUD: live report count + coordinates */}
      <div className="map-hud" aria-live="polite" aria-atomic="true">
        <div className="hud-brand" aria-hidden="true">
          <span className="hud-dot" />
          AccessPath
        </div>
        <div className="hud-stat">
          <span className="hud-stat-value">{reports.length}</span>
          <span className="hud-stat-label">
            {reports.length === 1 ? "report" : "reports"} visible
          </span>
        </div>
        <div className="hud-coords" aria-label="Map centre coordinates">
          25.2048° N · 55.2708° E
        </div>
      </div>

      {/* Legend toggle */}
      <div className="map-controls-row">
        <button
          className="map-ctrl-btn"
          onClick={() => setShowLegend((v) => !v)}
          aria-expanded={showLegend}
          aria-controls="map-legend"
          aria-label={showLegend ? "Hide report legend" : "Show report legend"}
        >
          {showLegend ? "Hide legend" : "Show legend"}
        </button>
      </div>

      {/* Floating legend (F-6, US-7, US-8 — labels + icons, not color alone) */}
      {showLegend && (
        <MapLegend
          id="map-legend"
          categoryCounts={categoryCounts}
        />
      )}

      <MapContainer
        center={[25.2048, 55.2708]}
        zoom={15}
        zoomControl={false}
        style={{ height: "100%", width: "100%" }}
        className="leaflet-map"
        // Leaflet container needs a role for screen readers
        // (set via portalled div — Leaflet doesn't expose this prop directly)
      >
        {/*
          CartoDB Voyager — clean, warm, readable dark-ish tiles.
          Lower visual noise than OSM default; not pure black (easier on eyes).
        */}
        <TileLayer
          attribution='&copy; <a href="https://carto.com/attributions">CARTO</a> &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
        />

        <ZoomControl position="bottomright" />
        <RecenterControl />

        {/* Route overlay */}
        <RouteOverlay />

        {/* Report markers */}
        {reports.map((report) => (
          <ReportMarker key={report.reportId} report={report} />
        ))}
      </MapContainer>
    </div>
  );
}