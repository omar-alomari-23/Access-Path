export type ActionType =
  | 'VERIFY'
  | 'REJECT'
  | 'RESOLVE'
  | 'EXPIRE'
  | 'REQUEST_CLARIFICATION';

export interface ModerationAction {
  actionId: string;
  reportId: string;
  moderatorUserId: string;
  actionType: ActionType;
  note: string | null;
  createdAt: string;
}

export interface ActionRequest {
  note?: string;
}
