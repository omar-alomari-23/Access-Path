import api from './api';
import type { CreateVoteRequest, VoteResponse } from '../types/vote';

export const voteService = {
  castVote: (reportId: string, data: CreateVoteRequest): Promise<VoteResponse> =>
    api
      .post<VoteResponse>(`/reports/${reportId}/votes`, data)
      .then((r) => r.data),
};
