import api from './api';
import type {
  Report,
  NearbyReport,
  DraftResponse,
  CreateReportRequest,
} from '../types/report';

export const reportService = {
  draft: (data: CreateReportRequest): Promise<DraftResponse> =>
    api.post<DraftResponse>('/reports/draft', data).then((r) => r.data),

  create: (data: CreateReportRequest): Promise<Report> =>
    api.post<Report>('/reports', data).then((r) => r.data),

  getById: (id: string): Promise<Report> =>
    api.get<Report>(`/reports/${id}`).then((r) => r.data),

  getNearby: (lat: number, lng: number, radius = 100): Promise<NearbyReport[]> =>
    api
      .get<NearbyReport[]>('/reports/nearby', { params: { lat, lng, radius } })
      .then((r) => r.data),
};
