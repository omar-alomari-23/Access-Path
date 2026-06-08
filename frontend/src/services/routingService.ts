import api from './api';
import type { RouteRequest, RouteResponse } from '../types/route';

export const routingService = {
  getRoutes: (data: RouteRequest): Promise<RouteResponse> =>
    api.post<RouteResponse>('/routes', data).then((r) => r.data),
};
