import api from './api';
import type { Report } from '../types/report';
import type { ModerationAction, ActionRequest } from '../types/moderation';

export const moderationService = {
  getQueue: (): Promise<Report[]> =>
    api.get<Report[]>('/moderation/queue').then((r) => r.data),

  verify: (reportId: string, data?: ActionRequest): Promise<ModerationAction> =>
    api
      .post<ModerationAction>(`/moderation/${reportId}/verify`, data ?? {})
      .then((r) => r.data),

  reject: (reportId: string, data?: ActionRequest): Promise<ModerationAction> =>
    api
      .post<ModerationAction>(`/moderation/${reportId}/reject`, data ?? {})
      .then((r) => r.data),

  resolve: (reportId: string, data?: ActionRequest): Promise<ModerationAction> =>
    api
      .post<ModerationAction>(`/moderation/${reportId}/resolve`, data ?? {})
      .then((r) => r.data),

  expire: (reportId: string): Promise<ModerationAction> =>
    api
      .post<ModerationAction>(`/moderation/${reportId}/expire`)
      .then((r) => r.data),
};
