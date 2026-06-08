import { useState } from 'react';
import MapView from '../components/map/MapView';
import FilterBar from '../components/reports/FilterBar';
import ReportForm from '../components/reports/ReportForm';
import ReportList from '../components/reports/ReportList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { useReports } from '../hooks/useReports';
import { useAuth } from '../context/AuthContext';
import type { ReportCategory } from '../types/report';

export default function MapPage() {
  const { isAuthenticated } = useAuth();
  const { reports, loading, error, addReport } = useReports();
  const [selectedCategory, setSelectedCategory] = useState<ReportCategory | 'all'>('all');

  const filteredReports =
    selectedCategory === 'all'
      ? reports
      : reports.filter((r) => r.category === selectedCategory);

  return (
    <div className="page-content">
      <FilterBar selected={selectedCategory} onChange={setSelectedCategory} />

      {error && <ErrorMessage message={error} />}

      {loading ? (
        <LoadingSpinner />
      ) : (
        <div className="map-page">
          <div className="map-section">
            <MapView reports={filteredReports} />
          </div>

          <div className="side-section" id="report-list">
            {isAuthenticated && (
              <ReportForm onSubmit={addReport} existingReports={reports} />
            )}
            <ReportList reports={filteredReports} />
          </div>
        </div>
      )}
    </div>
  );
}
