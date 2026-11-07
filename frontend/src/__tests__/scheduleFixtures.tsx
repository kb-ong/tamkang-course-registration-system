// Shared fixtures for FR4/FR5/FR7 frontend tests (not a test file itself).
import type { RegisteredCourse, RegistrationSchedule, RegistrationWindow } from "../api/types";
import { SEMESTERS } from "./coursesFixtures";

export const OPEN_PERIOD: RegistrationWindow = {
  startAt: "2026-08-01T00:00:00",
  endAt: "2026-12-31T23:59:59",
  open: true,
};
export const CLOSED_PERIOD: RegistrationWindow = { ...OPEN_PERIOD, open: false };
export const NO_PERIOD: RegistrationWindow = { startAt: null, endAt: null, open: false };

/** The spec's schedule fixture. Fresh copy each call. */
export function scheduleFixture(): RegistrationSchedule {
  return {
    semester: { ...SEMESTERS[0] },
    semesters: SEMESTERS.map((s) => ({ ...s })),
    registrationPeriod: { ...OPEN_PERIOD },
    courses: [
      { registrationId: 10, courseId: 1, code: "CS101", name: "Introduction to Programming", credits: 3, instructor: "Prof. Lee", semesterCode: "115-1", registeredAt: "2026-09-01T09:00:00" },
      { registrationId: 11, courseId: 2, code: "CS201", name: "Data Structures", credits: 3, instructor: "Prof. Chang", semesterCode: "115-1", registeredAt: "2026-09-02T10:30:00" },
    ],
    totalCredits: 6,
  };
}

export function fixtureCourses(): RegisteredCourse[] {
  return scheduleFixture().courses;
}

export function emptySchedule(): RegistrationSchedule {
  const s = scheduleFixture();
  s.courses = [];
  s.totalCredits = 0;
  return s;
}

export function onlyCs201(): RegistrationSchedule {
  const s = scheduleFixture();
  s.courses = s.courses.filter((c) => c.courseId === 2);
  s.totalCredits = 3;
  return s;
}

export function springSchedule(): RegistrationSchedule {
  return {
    semester: { ...SEMESTERS[1] },
    semesters: SEMESTERS.map((s) => ({ ...s })),
    registrationPeriod: { startAt: "2026-01-15T00:00:00", endAt: "2026-02-28T23:59:59", open: false },
    courses: [
      { registrationId: 12, courseId: 4, code: "CS102", name: "Object-Oriented Programming", credits: 3, instructor: "Prof. Lee", semesterCode: "114-2", registeredAt: "2026-02-01T09:00:00" },
    ],
    totalCredits: 3,
  };
}

export const DROP_RESULT = {
  message: "Successfully dropped CS101.",
  courseId: 1,
  courseCode: "CS101",
  availableSeats: 50,
};
