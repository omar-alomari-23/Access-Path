import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const navClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'active' : '';

export default function Navbar() {
  const { user, isAuthenticated, isModerator, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <NavLink to="/">AccessPath</NavLink>
      </div>

      <div className="navbar-links">
        <NavLink to="/" end className={navClass}>Map</NavLink>
        <NavLink to="/routes" className={navClass}>Route Planner</NavLink>
        {isModerator && (
          <NavLink to="/moderation" className={navClass}>Moderation</NavLink>
        )}
      </div>

      <div className="navbar-auth">
        {isAuthenticated ? (
          <>
            <span className="navbar-user">
              {user!.email} · {user!.role}
            </span>
            <button type="button" onClick={handleLogout} className="btn-outline">
              Logout
            </button>
          </>
        ) : (
          <>
            <NavLink to="/login" className={navClass}>Login</NavLink>
            <NavLink to="/register" className={navClass}>Register</NavLink>
          </>
        )}
      </div>
    </nav>
  );
}
