import api from './api';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/auth';

export const authService = {
  register: (data: RegisterRequest): Promise<AuthResponse> =>
    api.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: LoginRequest): Promise<AuthResponse> =>
    api.post<AuthResponse>('/auth/login', data).then((r) => r.data),
};
