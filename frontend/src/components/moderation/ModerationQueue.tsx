import { useState } from 'react';
import { moderationService } from '../../services/moderationService';
import type { Report } from '../../types/report';

interface ModerationQueueProps {
  reports: Report[];
  onActionDone: () => void;
}

export default function ModerationQueue({ reports, onActionDone }: ModerationQueueProps) {
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');

  const setNote = (id: string, value: string) =>
    setNotes((prev) => ({ ...prev, [id]: value }));

  const run = async (id: string, action: () => Promise<unknown>) => {
    setBusy(id);
    setError('');
    try {
      await action();
      onActionDone();
    } catch (err) {
      setError((err as Error).message || 'Action failed. Check permissions and try again.');
    } finally {
      setBusy(null);
    }
  };

  if (reports.length === 0) {
    return (
      <p style={{ color: 'rgba(226,232,240,0.45)', marginTop: '20px' }}>
        No reports pending moderation.
      </p>
    );
  }

  return (
    <div className="mod-queue">
      {error && (
        <div className="error-message" style={{ marginBottom: '12px' }}>
          {error}
        </div>
      )}

      {reports.map((report) => {
        const note = notes[report.reportId] ?? '';
        const isBusy = busy === report.reportId;

        return (
          <div key={report.reportId} className="mod-card">
            <div className="mod-card-header">
              <span className="mod-badge mod-badge-category">{report.category}</span>
              <span className={`mod-badge mod-badge-severity-${report.severity}`}>
                {report.severity}
              </span>
            </div>

            <p className="mod-description">{report.description}</p>

            <div className="mod-meta">
              {report.latitude.toFixed(5)}, {report.longitude.toFixed(5)}
              {' · '}
              {new Date(report.createdAt).toLocaleString()}
            </div>

            <textarea
              className="mod-note-input"
              rows={2}
              placeholder="Optional note…"
              value={note}
              onChange={(e) => setNote(report.reportId, e.target.value)}
              disabled={isBusy}
            />

            <div className="mod-actions">
              <button
                className="mod-btn mod-btn-verify"
                disabled={isBusy}
                onClick={() =>
                  run(report.reportId, () =>
                    moderationService.verify(report.reportId, { note: note || undefined })
                  )
                }
              >
                Verify
              </button>
              <button
                className="mod-btn mod-btn-reject"
                disabled={isBusy}
                onClick={() =>
                  run(report.reportId, () =>
                    moderationService.reject(report.reportId, { note: note || undefined })
                  )
                }
              >
                Reject
              </button>
              <button
                className="mod-btn mod-btn-resolve"
                disabled={isBusy}
                onClick={() =>
                  run(report.reportId, () =>
                    moderationService.resolve(report.reportId, { note: note || undefined })
                  )
                }
              >
                Resolve
              </button>
              <button
                className="mod-btn mod-btn-expire"
                disabled={isBusy}
                onClick={() =>
                  run(report.reportId, () =>
                    moderationService.expire(report.reportId)
                  )
                }
              >
                Expire
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
