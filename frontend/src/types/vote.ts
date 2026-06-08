export type VoteType = 'CONFIRM' | 'DISPUTE';

export interface VoteResponse {
  voteId: string;
  reportId: string;
  userId: string;
  voteType: VoteType;
  createdAt: string;
}

export interface CreateVoteRequest {
  voteType: VoteType;
}
