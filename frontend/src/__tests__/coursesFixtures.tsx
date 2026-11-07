// Shared fixtures for FR2/FR3 frontend tests (not a test file itself).
import type { CourseListing, SemesterOption } from "../api/types";

export const SEMESTERS: SemesterOption[] = [
  { code: "115-1", name: "2026 Fall", current: true },
  { code: "114-2", name: "2026 Spring", current: false },
];

/** The spec's listing fixture. Fresh copy each call. */
export function listingFixture(): CourseListing {
  return {
    semester: { ...SEMESTERS[0] },
    courses: [
      { id: 1, code: "CS101", name: "Introduction to Programming", credits: 3, instructor: "Prof. Lee", capacity: 50, availableSeats: 50, registered: false },
      { id: 2, code: "CS201", name: "Data Structures", credits: 3, instructor: "Prof. Chang", capacity: 40, availableSeats: 40, registered: false },
      { id: 3, code: "MA101", name: "Calculus I", credits: 4, instructor: "Prof. Huang", capacity: 1, availableSeats: 0, registered: true },
    ],
  };
}

export function springListing(): CourseListing {
  return {
    semester: { ...SEMESTERS[1] },
    courses: [
      { id: 4, code: "CS102", name: "Object-Oriented Programming", credits: 3, instructor: "Prof. Lee", capacity: 45, availableSeats: 45, registered: false },
    ],
  };
}

export const REG_RESULT = {
  message: "Successfully registered for CS101.",
  registrationId: 10,
  courseId: 1,
  courseCode: "CS101",
  availableSeats: 49,
};

export function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}
