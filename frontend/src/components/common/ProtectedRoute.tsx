import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import type { ReactNode } from 'react';
import type { UserRole } from '../../types/auth';

interface ProtectedRouteProps {
  children: ReactNode;
  requiredRole?: UserRole;
}

/**
 * Redirects to /login if not authenticated.
 * Redirects to / if authenticated but missing the required role.
 */
export default function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (requiredRole && user?.role !== requiredRole) {
    return (
      <div className="not-authorized">
        <h2>Access Denied</h2>
        <p>You need the <strong>{requiredRole}</strong> role to view this page.</p>
      </div>
    );
  }

  return <>{children}</>;
}
