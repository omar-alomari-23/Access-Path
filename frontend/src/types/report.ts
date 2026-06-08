export type ReportCategory =
  | 'STEP_FREE_ISSUE'
  | 'SURFACE_HAZARD'
  | 'OBSTRUCTION'
  | 'LIGHTING_ISSUE'
  | 'HEAT_NO_SHADE';

export type ReportSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export type ReportStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'RESOLVED' | 'EXPIRED';

export interface Report {
  reportId: string;
  reporterUserId: string;
  category: ReportCategory;
  severity: ReportSeverity;
  description: string;
  latitude: number;
  longitude: number;
  status: ReportStatus;
  confidenceScore: number;
  createdAt: string;
  expiresAt: string | null;
}

export interface NearbyReport {
  reportId: string;
  category: ReportCategory;
  severity: ReportSeverity;
  status: ReportStatus;
  latitude: number;
  longitude: number;
  confidenceScore: number;
  createdAt: string;
}

export interface DraftResponse {
  suggestedCategory: ReportCategory;
  suggestedSeverity: ReportSeverity;
  confidenceFlag: boolean;
  latitude: number;
  longitude: number;
  description: string;
}

export interface CreateReportRequest {
  description: string;
  latitude: number;
  longitude: number;
  category?: string;
  severity?: string;
}
