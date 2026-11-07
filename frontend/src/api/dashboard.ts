import { apiFetch } from './client';
import type { DashboardData } from './types';

export function fetchDashboard(): Promise<DashboardData> {
  return apiFetch<DashboardData>('/api/dashboard');
}
