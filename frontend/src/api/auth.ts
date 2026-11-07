import { ApiError, apiFetch } from './client';
import type { StudentProfile } from './types';

export function login(studentId: string, password: string): Promise<StudentProfile> {
  return apiFetch<StudentProfile>('/api/auth/login', {
    method: 'POST',
    body: { studentId, password },
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' });
}

export async function fetchMe(): Promise<StudentProfile | null> {
  try {
    return await apiFetch<StudentProfile>('/api/auth/me');
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      return null;
    }
    throw err;
  }
}
