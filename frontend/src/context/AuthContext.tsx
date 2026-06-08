import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from 'react';
import type { AuthUser, UserRole } from '../types/auth';

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isModerator: boolean;
  login: (user: AuthUser) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

function loadStoredUser(): AuthUser | null {
  try {
    const token = localStorage.getItem('token');
    const userId = localStorage.getItem('userId');
    const email = localStorage.getItem('email');
    const role = localStorage.getItem('role') as UserRole | null;
    if (token && userId && email && role) {
      if (isTokenExpired(token)) {
        localStorage.removeItem('token');
        localStorage.removeItem('userId');
        localStorage.removeItem('email');
        localStorage.removeItem('role');
        return null;
      }
      return { token, userId, email, role };
    }
  } catch {
    // storage unavailable
  }
  return null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser);

  const login = useCallback((incoming: AuthUser) => {
    localStorage.setItem('token', incoming.token);
    localStorage.setItem('userId', incoming.userId);
    localStorage.setItem('email', incoming.email);
    localStorage.setItem('role', incoming.role);
    setUser(incoming);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: user !== null,
        isModerator: user?.role === 'MODERATOR',
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
