import type { RegisteredCourse } from '../api/types';

const HEADER = 'Course Code,Course Name,Credits,Instructor,Semester,Registered At';

/** "2026-09-01T09:00:00" -> "2026-09-01 09:00" */
function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16);
}

/** RFC 4180 quoting: wrap in quotes when the field has a comma, quote, CR or LF. */
function quote(field: string): string {
  return /[",\r\n]/.test(field) ? `"${field.replace(/"/g, '""')}"` : field;
}

/** Text fields: neutralise spreadsheet formulas, then quote. */
function text(field: string): string {
  const guarded = /^[=+\-@\t]/.test(field) ? `'${field}` : field;
  return quote(guarded);
}

export function buildScheduleCsv(courses: RegisteredCourse[], totalCredits: number): string {
  const lines = [HEADER];
  for (const c of courses) {
    lines.push(
      [
        text(c.code),
        text(c.name),
        String(c.credits),
        text(c.instructor),
        quote(c.semesterCode),
        quote(formatDateTime(c.registeredAt)),
      ].join(','),
    );
  }
  lines.push(`Total Credits,,${totalCredits},,,`);
  return lines.join('\r\n');
}

export function scheduleCsvFileName(semesterCode: string): string {
  return `registered-courses-${semesterCode}.csv`;
}
