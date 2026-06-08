import { useState } from "react";
import { voteService } from "../../services/voteService";
import { useAuth } from "../../context/AuthContext";
import type { Report, ReportCategory } from "../../types/report";
import type { VoteType } from "../../types/vote";

interface ReportListProps {
  reports: Report[];
}

const categoryLabels: Record<ReportCategory, string> = {
  STEP_FREE_ISSUE: "Step-free issue",
  SURFACE_HAZARD: "Surface hazard",
  OBSTRUCTION: "Obstruction",
  LIGHTING_ISSUE: "Lighting issue",
  HEAT_NO_SHADE: "Heat / no shade",
};

const statusColors: Record<string, string> = {
  PENDING:  "rgba(251,191,36,0.75)",
  VERIFIED: "rgba(74,222,128,0.75)",
  REJECTED: "rgba(248,113,113,0.75)",
  RESOLVED: "rgba(96,165,250,0.75)",
  EXPIRED:  "rgba(148,163,184,0.5)",
};

function loadVotedIds(): Set<string> {
  try {
    const stored = localStorage.getItem('votedReports');
    return stored ? new Set(JSON.parse(stored)) : new Set();
  } catch {
    return new Set();
  }
}

export default function ReportList({ reports }: ReportListProps) {
  const { isAuthenticated, user } = useAuth();

  const [votedIds, setVotedIds] = useState<Set<string>>(loadVotedIds);
  const [voteState, setVoteState] = useState<Record<string, "idle" | "loading" | "done" | "error">>({});

  const handleVote = async (reportId: string, voteType: VoteType) => {
    setVoteState((prev) => ({ ...prev, [reportId]: "loading" }));
    try {
      await voteService.castVote(reportId, { voteType });
      setVoteState((prev) => ({ ...prev, [reportId]: "done" }));
      setVotedIds((prev) => {
        const next = new Set(prev).add(reportId);
        localStorage.setItem('votedReports', JSON.stringify([...next]));
        return next;
      });
    } catch {
      setVoteState((prev) => ({ ...prev, [reportId]: "error" }));
    }
  };

  if (reports.length === 0) {
    return (
      <div className="report-list">
        <h2>Reported Issues</h2>
        <p style={{ fontSize: "13px", color: "rgba(226,232,240,0.35)", margin: "0" }}>
          No reports in this category.
        </p>
      </div>
    );
  }

  return (
    <div className="report-list">
      <h2>Reported Issues</h2>
      <ul>
        {reports.map((report) => {
          const alreadyVoted = votedIds.has(report.reportId);
          const state = voteState[report.reportId] ?? (alreadyVoted ? "done" : "idle");
          const canVote =
            isAuthenticated &&
            user?.userId !== report.reporterUserId &&
            report.status !== "REJECTED" &&
            report.status !== "EXPIRED" &&
            !alreadyVoted &&
            state !== "done";

          return (
            <li key={report.reportId}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "8px" }}>
                <div>
                  <strong>{categoryLabels[report.category]}</strong>
                  {report.description ? `: ${report.description}` : ""}
                </div>
                <span
                  style={{
                    fontSize: "10px",
                    color: statusColors[report.status] ?? "rgba(226,232,240,0.4)",
                    fontFamily: "ui-monospace,monospace",
                    whiteSpace: "nowrap",
                    paddingTop: "2px",
                  }}
                >
                  {report.status}
                </span>
              </div>

              {/* Vote buttons — only for authenticated users on others' reports */}
              {isAuthenticated && (
                <div style={{ marginTop: "6px", display: "flex", gap: "6px" }}>
                  {state === "done" && (
                    <span style={{ fontSize: "11px", color: "rgba(74,222,128,0.7)" }}>
                      Vote recorded
                    </span>
                  )}
                  {state === "error" && (
                    <span style={{ fontSize: "11px", color: "rgba(248,113,113,0.7)" }}>
                      Already voted or not allowed
                    </span>
                  )}
                  {(state === "idle" || state === "loading") && canVote && (
                    <>
                      <button
                        type="button"
                        disabled={state === "loading"}
                        onClick={() => handleVote(report.reportId, "CONFIRM")}
                        style={{ fontSize: "11px", padding: "3px 9px", borderRadius: "5px", border: "1px solid rgba(74,222,128,0.3)", background: "rgba(74,222,128,0.08)", color: "rgba(74,222,128,0.85)", cursor: "pointer" }}
                      >
                        ✓ Confirm
                      </button>
                      <button
                        type="button"
                        disabled={state === "loading"}
                        onClick={() => handleVote(report.reportId, "DISPUTE")}
                        style={{ fontSize: "11px", padding: "3px 9px", borderRadius: "5px", border: "1px solid rgba(248,113,113,0.3)", background: "rgba(248,113,113,0.08)", color: "rgba(248,113,113,0.85)", cursor: "pointer" }}
                      >
                        ✗ Dispute
                      </button>
                    </>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
