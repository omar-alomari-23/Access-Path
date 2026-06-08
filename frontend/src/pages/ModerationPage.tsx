import { useState, useEffect, useCallback } from 'react';
import ModerationQueue from '../components/moderation/ModerationQueue';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { moderationService } from '../services/moderationService';
import type { Report } from '../types/report';

export default function ModerationPage() {
  const [queue, setQueue] = useState<Report[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadQueue = useCallback(() => {
    setLoading(true);
    setError('');
    moderationService
      .getQueue()
      .then(setQueue)
      .catch(() => setError('Failed to load moderation queue.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadQueue();
  }, [loadQueue]);

  return (
    <div className="page-content moderation-page">
      <h1>Moderation Queue</h1>

      {loading && <LoadingSpinner />}
      {error && <ErrorMessage message={error} />}

      {!loading && !error && (
        <ModerationQueue reports={queue} onActionDone={loadQueue} />
      )}
    </div>
  );
}
