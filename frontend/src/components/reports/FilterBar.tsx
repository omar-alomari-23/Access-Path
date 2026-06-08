import type { ReportCategory } from "../../types/report";

interface FilterBarProps {
  selected: ReportCategory | "all";
  onChange: (category: ReportCategory | "all") => void;
}

const categoryLabels: Record<ReportCategory, string> = {
  STEP_FREE_ISSUE: "Step-free",
  SURFACE_HAZARD: "Surface hazard",
  OBSTRUCTION: "Obstruction",
  LIGHTING_ISSUE: "Lighting",
  HEAT_NO_SHADE: "Heat / no shade",
};

export default function FilterBar({ selected, onChange }: FilterBarProps) {
  const categories: (ReportCategory | "all")[] = [
    "all",
    "STEP_FREE_ISSUE",
    "SURFACE_HAZARD",
    "OBSTRUCTION",
    "LIGHTING_ISSUE",
    "HEAT_NO_SHADE",
  ];

  return (
    <div className="filter-bar">
      {categories.map((category) => (
        <button
          key={category}
          type="button"
          onClick={() => onChange(category)}
          className={selected === category ? "active" : ""}
        >
          {category === "all" ? "ALL" : categoryLabels[category]}
        </button>
      ))}
    </div>
  );
}