export type UserRole = 'NAVIGATOR' | 'REPORTER' | 'MODERATOR';

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  role: UserRole;
}

export interface RegisterRequest {
  email: string;
  password: string;
  role: UserRole;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthUser {
  userId: string;
  email: string;
  role: UserRole;
  token: string;
}
