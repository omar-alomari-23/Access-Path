import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="not-authorized">
      <h2>404 — Page Not Found</h2>
      <p>The page you're looking for doesn't exist.</p>
      <Link to="/" style={{ marginTop: '8px', display: 'inline-block' }}>
        Back to Map
      </Link>
    </div>
  );
}
