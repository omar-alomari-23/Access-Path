import { useState } from "react";
import { reportService } from "../../services/reportService";
import type { Report, ReportCategory, ReportSeverity, DraftResponse } from "../../types/report";

interface ReportFormProps {
  onSubmit: (report: Report) => void;
  existingReports: Report[];
}

type Step = "editing" | "draft_loading" | "draft_review" | "submitting" | "duplicate_warning";

export default function ReportForm({ onSubmit, existingReports }: ReportFormProps) {
  const [step, setStep] = useState<Step>("editing");

  const [category, setCategory] = useState<ReportCategory>("OBSTRUCTION");
  const [severity, setSeverity] = useState<ReportSeverity>("MEDIUM");
  const [description, setDescription] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [draft, setDraft] = useState<DraftResponse | null>(null);
  const [pendingReport, setPendingReport] = useState<Report | null>(null);
  const [apiError, setApiError] = useState("");

  const resetForm = () => {
    setStep("editing");
    setCategory("OBSTRUCTION");
    setSeverity("MEDIUM");
    setDescription("");
    setLatitude("");
    setLongitude("");
    setDraft(null);
    setPendingReport(null);
    setApiError("");
  };

  const isDuplicate = (lat: number, lng: number, cat: ReportCategory) =>
    existingReports.some(
      (r) =>
        r.category === cat &&
        Math.abs(r.latitude - lat) < 0.003 &&
        Math.abs(r.longitude - lng) < 0.003
    );

  // ── Step 1: get AI draft suggestions ────────────────────
  const handleGetSuggestions = async () => {
    if (!description.trim() || !latitude.trim() || !longitude.trim()) {
      setApiError("Please fill in description and coordinates.");
      return;
    }
    setApiError("");
    setStep("draft_loading");
    try {
      const result = await reportService.draft({
        description,
        latitude: parseFloat(latitude),
        longitude: parseFloat(longitude),
        category,
        severity,
      });
      setDraft(result);
      setCategory(result.suggestedCategory as ReportCategory);
      setSeverity(result.suggestedSeverity as ReportSeverity);
      setStep("draft_review");
    } catch {
      setApiError("AI suggestions unavailable. Review your category/severity and submit directly.");
      setStep("editing");
    }
  };

  // ── Step 2: submit confirmed report to backend ───────────
  const handleSubmitFinal = async () => {
    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);

    if (isDuplicate(lat, lng, category)) {
      const fake: Report = {
        reportId: crypto.randomUUID(),
        reporterUserId: "",
        category,
        severity,
        description,
        latitude: lat,
        longitude: lng,
        status: "PENDING",
        confidenceScore: 0.5,
        createdAt: new Date().toISOString(),
        expiresAt: null,
      };
      setPendingReport(fake);
      setStep("duplicate_warning");
      return;
    }

    await submitToBackend(lat, lng);
  };

  const submitToBackend = async (lat: number, lng: number) => {
    setStep("submitting");
    setApiError("");
    try {
      const saved = await reportService.create({
        description,
        latitude: lat,
        longitude: lng,
        category,
        severity,
      });
      onSubmit(saved);
      resetForm();
    } catch (err) {
      const msg = (err as Error).message ?? '';
      // Server-side validation rejections (e.g. duplicate) — show error, stay on form
      if (msg && !msg.toLowerCase().includes('network') && !msg.toLowerCase().includes('unexpected')) {
        setApiError(msg);
        setStep("editing");
        return;
      }
      // True network failure — add locally so the user doesn't lose their entry
      const local: Report = {
        reportId: crypto.randomUUID(),
        reporterUserId: "",
        category,
        severity,
        description,
        latitude: lat,
        longitude: lng,
        status: "PENDING",
        confidenceScore: 0.5,
        createdAt: new Date().toISOString(),
        expiresAt: null,
      };
      onSubmit(local);
      resetForm();
    }
  };

  const handleSubmitAnyway = () => {
    if (pendingReport) {
      onSubmit(pendingReport);
    }
    resetForm();
  };

  const fieldStyle = {
    width: "100%",
    padding: "0.7rem",
    borderRadius: "8px",
    border: "1px solid #334155",
    background: "#0f172a",
    color: "#e2e8f0",
    boxSizing: "border-box" as const,
    fontFamily: "inherit",
    fontSize: "13px",
  };

  const labelStyle = {
    display: "flex",
    flexDirection: "column" as const,
    gap: "0.35rem",
    fontSize: "13px",
    fontWeight: 500 as const,
    color: "rgba(226,232,240,0.7)",
  };

  const isBusy = step === "draft_loading" || step === "submitting";

  return (
    <div
      className="report-form"
      style={{
        background: "rgba(22,32,50,0.9)",
        border: "1px solid rgba(255,255,255,0.07)",
        padding: "1.25rem",
        borderRadius: "14px",
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
      }}
    >
      <h2 style={{ margin: 0, fontSize: "14px", fontWeight: 700, color: "rgba(226,232,240,0.5)", textTransform: "uppercase", letterSpacing: "0.1em", fontFamily: "ui-monospace,monospace" }}>
        Submit Report
      </h2>

      {/* ── Fields ── */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
        <label style={labelStyle}>
          Category
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value as ReportCategory)}
            style={fieldStyle}
            disabled={isBusy}
          >
            <option value="STEP_FREE_ISSUE">Step-free issue</option>
            <option value="SURFACE_HAZARD">Surface hazard</option>
            <option value="OBSTRUCTION">Obstruction</option>
            <option value="LIGHTING_ISSUE">Lighting issue</option>
            <option value="HEAT_NO_SHADE">Heat / no shade</option>
          </select>
        </label>

        <label style={labelStyle}>
          Severity
          <select
            value={severity}
            onChange={(e) => setSeverity(e.target.value as ReportSeverity)}
            style={fieldStyle}
            disabled={isBusy}
          >
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
          </select>
        </label>
      </div>

      <label style={labelStyle}>
        Description
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Describe the issue"
          rows={3}
          style={{ ...fieldStyle, resize: "vertical" as const }}
          disabled={isBusy}
        />
      </label>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.75rem" }}>
        <label style={labelStyle}>
          Latitude
          <input
            type="number"
            value={latitude}
            onChange={(e) => setLatitude(e.target.value)}
            placeholder="25.2048"
            style={fieldStyle}
            disabled={isBusy}
          />
        </label>
        <label style={labelStyle}>
          Longitude
          <input
            type="number"
            value={longitude}
            onChange={(e) => setLongitude(e.target.value)}
            placeholder="55.2708"
            style={fieldStyle}
            disabled={isBusy}
          />
        </label>
      </div>

      {/* ── AI draft suggestion banner ── */}
      {step === "draft_review" && draft && (
        <div style={{ padding: "0.75rem 1rem", borderRadius: "9px", background: "rgba(96,165,250,0.1)", border: "1px solid rgba(96,165,250,0.25)", fontSize: "12px", color: "#93c5fd" }}>
          AI suggested: <strong>{draft.suggestedCategory}</strong> · <strong>{draft.suggestedSeverity}</strong>
          {draft.confidenceFlag ? " ✓ High confidence" : " — low confidence, please review"}
        </div>
      )}

      {apiError && (
        <div style={{ fontSize: "12px", color: "#fca5a5", padding: "8px 12px", borderRadius: "8px", background: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.25)" }}>
          {apiError}
        </div>
      )}

      {/* ── Actions ── */}
      {step === "editing" && (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          <button
            type="button"
            onClick={handleGetSuggestions}
            style={{ padding: "0.75rem", borderRadius: "9px", border: "none", background: "#2563eb", color: "#fff", fontWeight: 600, cursor: "pointer", fontSize: "13px" }}
          >
            Get AI Suggestions
          </button>
          {apiError && (
            <button
              type="button"
              onClick={handleSubmitFinal}
              style={{ padding: "0.75rem", borderRadius: "9px", border: "1px solid #475569", background: "transparent", color: "rgba(226,232,240,0.7)", cursor: "pointer", fontSize: "13px" }}
            >
              Submit Directly
            </button>
          )}
        </div>
      )}

      {step === "draft_loading" && (
        <button type="button" disabled style={{ padding: "0.75rem", borderRadius: "9px", border: "none", background: "#1e3a8a", color: "#93c5fd", fontWeight: 600, fontSize: "13px" }}>
          Getting suggestions…
        </button>
      )}

      {step === "draft_review" && (
        <div style={{ display: "flex", gap: "0.6rem" }}>
          <button
            type="button"
            onClick={handleSubmitFinal}
            style={{ flex: 1, padding: "0.75rem", borderRadius: "9px", border: "none", background: "#16a34a", color: "#fff", fontWeight: 600, cursor: "pointer", fontSize: "13px" }}
          >
            Confirm & Submit
          </button>
          <button
            type="button"
            onClick={() => setStep("editing")}
            style={{ padding: "0.75rem 1rem", borderRadius: "9px", border: "1px solid #334155", background: "transparent", color: "rgba(226,232,240,0.6)", cursor: "pointer", fontSize: "13px" }}
          >
            Edit
          </button>
        </div>
      )}

      {step === "submitting" && (
        <button type="button" disabled style={{ padding: "0.75rem", borderRadius: "9px", border: "none", background: "#14532d", color: "#86efac", fontWeight: 600, fontSize: "13px" }}>
          Submitting…
        </button>
      )}

      {/* ── Duplicate warning ── */}
      {step === "duplicate_warning" && (
        <div style={{ padding: "1rem", border: "1px solid rgba(251,146,60,0.4)", borderRadius: "10px", background: "rgba(251,146,60,0.08)" }}>
          <p style={{ margin: "0 0 0.75rem", fontSize: "13px", color: "#fdba74" }}>
            A similar report already exists nearby.
          </p>
          <div style={{ display: "flex", gap: "0.6rem" }}>
            <button
              type="button"
              onClick={handleSubmitAnyway}
              style={{ padding: "0.65rem 1rem", borderRadius: "8px", border: "none", background: "#d97706", color: "#fff", fontWeight: 600, cursor: "pointer", fontSize: "12px" }}
            >
              Submit Anyway
            </button>
            <button
              type="button"
              onClick={() => setStep("editing")}
              style={{ padding: "0.65rem 1rem", borderRadius: "8px", border: "1px solid #334155", background: "transparent", color: "rgba(226,232,240,0.6)", cursor: "pointer", fontSize: "12px" }}
            >
              Go Back
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
