import { apiFetch } from './client';
import type { CourseListing, RegistrationResult, SemesterOption } from './types';

export function fetchSemesters(): Promise<SemesterOption[]> {
  return apiFetch<SemesterOption[]>('/api/semesters');
}

export function fetchCourses(params: { semester?: string; q?: string }): Promise<CourseListing> {
  const search = new URLSearchParams();
  if (params.semester) search.set('semester', params.semester);
  if (params.q) search.set('q', params.q);
  const qs = search.toString();
  return apiFetch<CourseListing>(qs ? `/api/courses?${qs}` : '/api/courses');
}

export function registerForCourse(courseId: number): Promise<RegistrationResult> {
  return apiFetch<RegistrationResult>('/api/registrations', {
    method: 'POST',
    body: { courseId },
  });
}
