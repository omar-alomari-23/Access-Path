// MapLegend.tsx
// Displays all report categories with a color swatch, icon, label, and count.
// Uses both icon AND color for each entry — complies with F-6 / US-7 (non-color encoding).

interface MapLegendProps {
  id?: string;
  categoryCounts: Record<string, number>;
}

const legendItems: {
  category: string;
  label: string;
  icon: string;
  color: string;
}[] = [
  { category: "STEP_FREE_ISSUE", label: "Step-free Issue",  icon: "♿", color: "#f87171" },
  { category: "SURFACE_HAZARD",  label: "Surface Hazard",   icon: "⚠",  color: "#fb923c" },
  { category: "OBSTRUCTION",     label: "Obstruction",      icon: "🚧", color: "#e879f9" },
  { category: "LIGHTING_ISSUE",  label: "Lighting Issue",   icon: "💡", color: "#a78bfa" },
  { category: "HEAT_NO_SHADE",   label: "Heat / No Shade",  icon: "☀",  color: "#fbbf24" },
];

export default function MapLegend({ id, categoryCounts }: MapLegendProps) {
  return (
    <nav
      id={id}
      className="map-legend"
      aria-label="Report category legend"
    >
      <div className="legend-title" aria-hidden="true">
        Report types
      </div>
      <ul className="legend-items" role="list">
        {legendItems.map(({ category, label, icon, color }) => {
          const count = categoryCounts[category] ?? 0;
          return (
            <li key={category} className="legend-item">
              {/* Color swatch */}
              <span
                className="legend-swatch"
                style={{ background: color }}
                aria-hidden="true"
              />
              {/* Icon — redundant cue alongside color */}
              <span
                className="report-marker-icon"
                aria-hidden="true"
                style={{ fontSize: "13px", lineHeight: "1", flexShrink: 0 }}
              >
                {icon}
              </span>
              {/* Label */}
              <span className="legend-item-text">{label}</span>
              {/* Count */}
              <span
                className="legend-count"
                aria-label={`${count} ${label} report${count !== 1 ? "s" : ""}`}
              >
                {count}
              </span>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
