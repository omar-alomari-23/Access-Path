import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Auto-logout when a non-auth request gets 401 (token expired mid-session)
    if (
      error.response?.status === 401 &&
      !error.config?.url?.includes('/auth/')
    ) {
      localStorage.removeItem('token');
      localStorage.removeItem('userId');
      localStorage.removeItem('email');
      localStorage.removeItem('role');
      window.location.href = '/login';
    }
    // Surface the most specific message from the backend response body
    const message =
      error.response?.data?.message ??
      error.response?.data?.error ??
      error.message ??
      'An unexpected error occurred';
    return Promise.reject(new Error(message));
  }
);

export default api;
