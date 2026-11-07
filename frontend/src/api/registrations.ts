import { apiFetch } from './client';
import type { DropResult, RegistrationSchedule } from './types';

export function fetchRegistrations(semester?: string): Promise<RegistrationSchedule> {
  const search = new URLSearchParams();
  if (semester) search.set('semester', semester);
  const qs = search.toString();
  return apiFetch<RegistrationSchedule>(qs ? `/api/registrations?${qs}` : '/api/registrations');
}

export function dropCourse(courseId: number): Promise<DropResult> {
  return apiFetch<DropResult>(`/api/registrations/${courseId}`, { method: 'DELETE' });
}
