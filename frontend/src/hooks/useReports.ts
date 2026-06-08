import { useState, useEffect, useCallback } from 'react';
import { reportService } from '../services/reportService';
import { mockReports } from '../data/mockReports';
import type { Report } from '../types/report';

interface UseReportsOptions {
  lat?: number;
  lng?: number;
  radius?: number;
}

interface UseReportsResult {
  reports: Report[];
  loading: boolean;
  error: string;
  addReport: (report: Report) => void;
  reload: () => void;
}

/**
 * Fetches nearby reports from the backend and falls back to mock data
 * when the backend is not reachable.
 */
export function useReports({
  lat = 25.2048,
  lng = 55.2708,
  radius = 2000,
}: UseReportsOptions = {}): UseReportsResult {
  const [reports, setReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    reportService
      .getNearby(lat, lng, radius)
      .then((nearby) => {
        const mapped: Report[] = nearby.map((n) => ({
          ...n,
          reporterUserId: '',
          description: '',
          expiresAt: null,
        }));
        setReports(mapped);
      })
      .catch(() => {
        setReports(mockReports);
        setError('Could not reach backend — showing demo data.');
      })
      .finally(() => setLoading(false));
  }, [lat, lng, radius]);

  useEffect(() => {
    load();
  }, [load]);

  const addReport = useCallback((report: Report) => {
    setReports((prev) => [report, ...prev]);
  }, []);

  return { reports, loading, error, addReport, reload: load };
}
