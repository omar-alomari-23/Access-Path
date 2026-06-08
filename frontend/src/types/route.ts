export interface WayPoint {
  latitude: number;
  longitude: number;
}

export interface RouteWarning {
  category: string;
  severity: string;
  latitude: number;
  longitude: number;
  description: string;
}

export interface RouteOption {
  routeId: string;
  name: string;
  waypoints: WayPoint[];
  steps: string[];
  suitabilityScore: number;
  warnings: RouteWarning[];
  confidenceScore: number;
  estimatedMinutes: number;
}

export interface RouteRequest {
  originLat: number;
  originLng: number;
  destinationLat: number;
  destinationLng: number;
  avoidStairs?: boolean;
  avoidSteepInclines?: boolean;
  avoidUnsafeAreas?: boolean;
}

export interface RouteResponse {
  routes: RouteOption[];
}
