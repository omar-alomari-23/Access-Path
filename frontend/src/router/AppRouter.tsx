import { Routes, Route } from 'react-router-dom';
import ProtectedRoute from '../components/common/ProtectedRoute';
import MapPage from '../pages/MapPage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import ModerationPage from '../pages/ModerationPage';
import RoutePage from '../pages/RoutePage';
import NotFoundPage from '../pages/NotFoundPage';

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<MapPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/routes" element={<RoutePage />} />
      <Route
        path="/moderation"
        element={
          <ProtectedRoute requiredRole="MODERATOR">
            <ModerationPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
