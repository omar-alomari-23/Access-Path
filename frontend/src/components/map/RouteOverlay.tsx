import { Polyline, Popup } from "react-leaflet";

// Demo accessible walking route through Dubai Marina area
const demoRoute: [number, number][] = [
  [25.2035, 55.2685],
  [25.2048, 55.2708],
  [25.2065, 55.2725],
  [25.208,  55.274],
];

export default function RouteOverlay() {
  return (
    <>
      {/* Soft shadow under the route line for legibility */}
      <Polyline
        positions={demoRoute}
        pathOptions={{
          color:   "rgba(0,0,0,0.35)",
          weight:  9,
          opacity: 1,
        }}
      />

      {/* Main route — muted blue, readable on both dark + light tiles */}
      <Polyline
        positions={demoRoute}
        pathOptions={{
          color:     "#60a5fa",
          weight:    5,
          opacity:   0.85,
          dashArray: undefined,
          lineCap:   "round",
          lineJoin:  "round",
        }}
      >
        <Popup>
          <div className="report-popup" aria-label="Route information">
            <div className="report-popup-header">
              <span className="report-popup-icon" aria-hidden="true">🗺</span>
              <strong className="report-popup-title">Accessible Walking Route</strong>
            </div>
            <p className="report-popup-desc">
              Step-free, shaded route — avoids stairs and known hazards in this area.
            </p>
            <div className="report-popup-meta">
              <span>~8 min walk</span>
              <span className="report-popup-status">Demo</span>
            </div>
          </div>
        </Popup>
      </Polyline>
    </>
  );
}
