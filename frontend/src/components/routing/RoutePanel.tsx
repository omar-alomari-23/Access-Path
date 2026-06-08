import { useState } from 'react';
import { routingService } from '../../services/routingService';
import type { RouteOption, RouteRequest } from '../../types/route';
import LoadingSpinner from '../common/LoadingSpinner';
import ErrorMessage from '../common/ErrorMessage';

export default function RoutePanel() {
  const [originLat, setOriginLat] = useState('');
  const [originLng, setOriginLng] = useState('');
  const [destLat, setDestLat] = useState('');
  const [destLng, setDestLng] = useState('');
  const [avoidStairs, setAvoidStairs] = useState(false);
  const [avoidInclines, setAvoidInclines] = useState(false);
  const [avoidUnsafe, setAvoidUnsafe] = useState(false);

  const [routes, setRoutes] = useState<RouteOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSearch = async () => {
    if (!originLat || !originLng || !destLat || !destLng) {
      setError('Please fill in all coordinates.');
      return;
    }
    setError('');
    setLoading(true);
    setRoutes([]);

    const req: RouteRequest = {
      originLat: parseFloat(originLat),
      originLng: parseFloat(originLng),
      destinationLat: parseFloat(destLat),
      destinationLng: parseFloat(destLng),
      avoidStairs,
      avoidSteepInclines: avoidInclines,
      avoidUnsafeAreas: avoidUnsafe,
    };

    try {
      const res = await routingService.getRoutes(req);
      setRoutes(res.routes);
    } catch {
      setError('Failed to fetch routes. Make sure the backend is running.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="route-form-grid">
        <label className="route-label">
          Origin Latitude
          <input
            className="route-input"
            type="number"
            placeholder="25.2048"
            value={originLat}
            onChange={(e) => setOriginLat(e.target.value)}
          />
        </label>

        <label className="route-label">
          Origin Longitude
          <input
            className="route-input"
            type="number"
            placeholder="55.2708"
            value={originLng}
            onChange={(e) => setOriginLng(e.target.value)}
          />
        </label>

        <label className="route-label">
          Destination Latitude
          <input
            className="route-input"
            type="number"
            placeholder="25.220"
            value={destLat}
            onChange={(e) => setDestLat(e.target.value)}
          />
        </label>

        <label className="route-label">
          Destination Longitude
          <input
            className="route-input"
            type="number"
            placeholder="55.280"
            value={destLng}
            onChange={(e) => setDestLng(e.target.value)}
          />
        </label>

        <div className="route-form-full">
          <div className="route-checkboxes">
            <label className="route-check-label">
              <input
                type="checkbox"
                checked={avoidStairs}
                onChange={(e) => setAvoidStairs(e.target.checked)}
              />
              Avoid stairs
            </label>
            <label className="route-check-label">
              <input
                type="checkbox"
                checked={avoidInclines}
                onChange={(e) => setAvoidInclines(e.target.checked)}
              />
              Avoid steep inclines
            </label>
            <label className="route-check-label">
              <input
                type="checkbox"
                checked={avoidUnsafe}
                onChange={(e) => setAvoidUnsafe(e.target.checked)}
              />
              Avoid unsafe areas
            </label>
          </div>
        </div>

        <div className="route-form-full">
          <button
            className="btn-primary"
            style={{ width: '100%' }}
            onClick={handleSearch}
            disabled={loading}
          >
            {loading ? 'Finding routes…' : 'Find Accessible Routes'}
          </button>
        </div>
      </div>

      {error && <ErrorMessage message={error} />}
      {loading && <LoadingSpinner />}

      {routes.length > 0 && (
        <div className="route-results">
          {routes.map((route) => (
            <div key={route.routeId} className="route-card">
              <div className="route-card-header">
                <span className="route-card-title">{route.name}</span>
                <span className="route-card-meta">
                  ~{route.estimatedMinutes} min · suitability {(route.suitabilityScore * 100).toFixed(0)}%
                </span>
              </div>

              {route.steps.length > 0 && (
                <ol className="route-steps">
                  {route.steps.map((step, i) => (
                    <li key={i}>{step}</li>
                  ))}
                </ol>
              )}

              {route.warnings.length > 0 && (
                <div className="route-warnings">
                  {route.warnings.map((w, i) => (
                    <div key={i} className="route-warning">
                      ⚠ {w.description} ({w.category} · {w.severity})
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </>
  );
}
