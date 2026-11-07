# Spec: FR6-capacity — Course Capacity Validation

Requirements covered: FR6 (AC1–AC3). Also keeps NFR2 (authenticated endpoints) and NFR4 (only successful registrations are audited) true.
Status: Draft (approved at the /build-feature human gate)
Builds on: `docs/specs/FR2-FR3-courses-register.md` (FR3). Where this spec contradicts FR3, this spec wins and §6 lists every FR3 criterion and test that changes.

## 1. Summary
A student who tries to register for a course with no seats left is blocked and sees a message that starts with **Course Full**. No registration row, no audit row and no seat change happens. On `/courses`, full courses show a red **Full** tag and a disabled Register button with a short explanation. If a course fills between page load and click, the server still rejects with the Course Full message, and the page refetches so the row turns into the full state. The database also refuses any `availableSeats` below 0 or above `capacity`. When N students race for the last seat, exactly one succeeds and every other student gets the Course Full message.

**Build slices.** Slice A, backend: §3.1–§3.3 and all *backend* criteria. Slice B, frontend: §3.4–§3.5 and all *frontend* criteria. Slice B depends only on the §3.3 message contract, and its tests mock `../api/courses`. No split is needed.

## 2. Acceptance criteria

Test levels:
- **backend**: JUnit 5 under `src/test/java`. HTTP tests use `@SpringBootTest` + `@AutoConfigureMockMvc` against the seed data, with `spring-security-test` (`csrf()`, `user("410000001").roles("STUDENT")`) and the existing `ApiTestSupport.jsonError(status, message)` matcher, so the error body is exactly `{"message":"X"}` and has no other keys. Service and concurrency tests call the beans directly.
- **frontend**: Vitest + React Testing Library under `frontend/src/__tests__/`, with `../api/courses` mocked as in FR3. The `it` name starts with the criterion ID.

Fixed seed facts (unchanged seed): MA101 in `115-1` has capacity 1, 0 seats, and Carol (`410000003`) is registered for it. Alice is `410000001` and Bob is `410000002`. Let `FULL_MSG(code)` mean the exact string `Course Full: {code} has no available seats.` (§3.4).

### FR6: Capacity validation (backend)
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR6-AC1 | Blocked at 0 seats. Given MA101 has 0 seats, when Bob (no registration) calls `RegistrationService.register("410000002", ma101Id)`, then it throws `CourseFullException`, and MA101 `availableSeats` is still 0 and `version` is unchanged. Via `POST /api/registrations` as Bob with `{"courseId": ma101Id}`, the status is 409. Also with a course `ZERO1` (capacity 5, seats 0, created by the test) and Alice: 409, with the course unchanged. | backend |
| FR6-AC2a | Message. In the cases of FR6-AC1, the response is `jsonError(409, "Course Full: MA101 has no available seats.")` and for `ZERO1` it is `"Course Full: ZERO1 has no available seats."`. `new CourseFullException("MA101").getMessage()` equals `Course Full: MA101 has no available seats.` | backend |
| FR6-AC2b | The message always contains the exact substring `Course Full` (case-sensitive) and never the generic conflict text `The course was just updated by another student. Please try again.` | backend |
| FR6-AC3a | No record created. After each rejected attempt of FR6-AC1, the `registration` row count equals its value before the call (seed: 1), the `audit_log` row count equals its value before (seed: 0), and the course `availableSeats` and `version` are unchanged. Rejected attempts are not audited (NFR4, same as FR3-EX9). | backend |
| FR6-AC3b | Last seat taken, then full. Given a course `LAST1` (capacity 1, seats 1), when Alice registers (201, `availableSeats` 0) and Bob then registers, then Bob gets 409 `Course Full: LAST1 has no available seats.`, seats stay 0, there is exactly 1 registration and exactly 1 `REGISTER` audit row for `LAST1`, and the audit row belongs to Alice. | backend |
| FR6-AC3c | Concurrent race, one seat. Given a course with capacity 1 and 1 seat and 10 students (the 3 seed students plus 7 created by the test through `StudentRepository`), when all 10 call `register` simultaneously (threads released together by a latch), then exactly 1 call returns and exactly 9 throw `CourseFullException`. No other exception type is accepted. Afterwards `availableSeats` is 0, there is exactly 1 registration and exactly 1 audit row for the course. Repeat for 10 rounds (re-creating the course each round), and the invariants hold in every round. | backend |
| FR6-AC3d | Concurrent race, k seats. Given capacity 3 with 3 seats and the same 10 students, when all 10 register at once, then exactly 3 return and 7 throw `CourseFullException`. Seats are 0 (never negative), with exactly 3 registrations and 3 audit rows for the course. | backend |
| FR6-EX1 | Message after retries. When the race loser's first attempt hits an optimistic-lock conflict, the retry re-reads the course and the final outcome is `CourseFullException` (not `RegistrationConflictException`). Deterministic test: `CourseRepository` is a `@MockitoSpyBean` where the first `saveAndFlush(any(Course))` first makes another committed change that takes the last seat, then throws `ObjectOptimisticLockingFailureException` (or, equivalently, the test registers Alice for the 1-seat course inside the stub before throwing), and then Bob's `register` ends with `CourseFullException` with message `Course Full: {code} has no available seats.`. After it there is exactly 1 registration. The test is not `@Transactional`. | backend |
| FR6-EX2 | Final full check after exhausted retries. With the spy of FR3-EX11 (every `saveAndFlush(any(Course))` throws `ObjectOptimisticLockingFailureException`) and a course whose seats are 0 at the moment attempts are exhausted (the stub sets the seats to 0 through a separate committed update before throwing, on every call), `register` throws `CourseFullException` after 5 attempts, not `RegistrationConflictException`. FR3-EX11 itself (course still has seats, so the conflict message) is unchanged and must still pass. | backend |
| FR6-EX3 | Database safeguard. Updating a course so that `availableSeats` is -1, or `availableSeats` is greater than `capacity` (for example 51 for CS101), fails. Both a JDBC `UPDATE` and `courseRepository.saveAndFlush` throw `DataIntegrityViolationException`. Setting seats 0 and seats = capacity succeeds. The test runs `@Transactional` (rolled back). Creating a course with `capacity` 1 and `availableSeats` 2 is rejected the same way. | backend |
| FR6-EX4 | Seat invariant on seed data. For every course in the database: `availableSeats + (count of registrations for that course) = capacity`. Seed: CS101 50+0, CS201 40+0, MA101 0+1 = 1, CS102 45+0. | backend |
| FR6-EX5 | Seat invariant after activity. After FR6-AC3b, FR6-AC3c and FR6-AC3d, the invariant of FR6-EX4 holds for the test course. | backend |
| FR6-EX6 | Check order is unchanged. A student who is already registered for a full course gets 409 `You are already registered for MA101.` (Carol posting MA101). That is the FR3-AC4b behaviour, unchanged, and it is not the Course Full message. A nonexistent course is still 404 `Course not found.` and no student is still 404 `Student not found.` | backend |
| FR6-EX7 | Anonymous, or a missing CSRF token, on `POST /api/registrations` for the full MA101 still gives 401 `Authentication required.` and 403 `Invalid or missing CSRF token.` (not 409). Nothing is written. (NFR2) | backend |
| FR6-EX8 | A course with seats left is unaffected. FR3-AC1, FR3-AC3 and FR3-EX12 to FR3-EX14 keep passing as they are. | backend |

### FR6: UI (frontend)
Common setup: as in FR3, with the **listing fixture**, except that for these tests MA101 (id 3, capacity 1, 0 seats) has `registered: false` unless a test says otherwise. Call this the *full fixture*. CS101 (id 1, 50/50) and CS201 (id 2, 40/40) are not full.

| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR6-UI1 | Full status. Given the full fixture, then `[data-testid="full-tag-3"]` is in the document with text `Full`, and `full-tag-1` and `full-tag-2` are not in the document. `course-seats-3` still has text exactly `0`, and `course-seats-1` is exactly `50`. | frontend |
| FR6-UI2 | Disabled button with explanation. Given the full fixture, then `register-btn-3` is disabled and has text `Register`, and `[data-testid="full-note-3"]` has text `No seats available.` Clicking `register-btn-3` does not call `registerForCourse`. `register-btn-1` is enabled and `full-note-1` is not in the document. | frontend |
| FR6-UI3 | Full and registered. Given the listing fixture with MA101 `registered: true` (the FR3 fixture), then `full-tag-3` has text `Full`, `registered-tag-3` has text `Registered`, `register-btn-3` is disabled, and `full-note-3` is not in the document (the user is told they are registered, not that they cannot register). | frontend |
| FR6-UI4 | Course fills between load and click. Given the full fixture where CS101 shows 1 seat (`availableSeats: 1`), when `registerForCourse` rejects with `new ApiError(409, "Course Full: CS101 has no available seats.")` and the refetch resolves the listing with CS101 at 0 seats and `registered: false`, then `register-error` has text `Course Full: CS101 has no available seats.`, `register-success` is not in the document, `fetchCourses` is called a second time with the current filters, and after the refetch `full-tag-1` is in the document with text `Full`, `register-btn-1` is disabled, `course-seats-1` is `0`, and `full-note-1` is `No seats available.` The error alert stays visible after the refetch. | frontend |
| FR6-UI5 | Success makes the last seat full. Given CS101 shows 1 seat and `registerForCourse` resolves `{message:"Successfully registered for CS101.",registrationId:10,courseId:1,courseCode:"CS101",availableSeats:0}`, then `course-seats-1` becomes `0`, `registered-tag-1` appears, `full-tag-1` appears, `register-btn-1` is disabled, and `full-note-1` is not in the document. | frontend |
| FR6-UI6 | A course with `availableSeats` 0 is "full" even if `capacity` is 0 or the seats value is negative (the UI treats `availableSeats <= 0` as full). The test uses a row with `capacity: 5, availableSeats: -1` and sees `full-tag`, `full-note` and a disabled button. | frontend |
| FR6-UI7 | Message not rewritten. The page shows the server's `ApiError.message` as is for a 409, and does not branch on the text. A 409 with another message (`You are already registered for CS101.`) still shows that message in `register-error` and refetches (FR3-UI4 behaviour, unchanged). | frontend |
| FR6-UI8 | Semester and search keep working. After the user switches to `114-2` and the listing contains a full course, the full state is shown for that course in the same way (`full-tag-{id}`, `full-note-{id}`, disabled button). | frontend |

## 3. Contract

### 3.1 Domain / data model
One change, to `Course` (package `edu.tku.registration.domain`). Add the Hibernate check constraint, so the generated H2 schema has it:
```java
@org.hibernate.annotations.Check(name = "ck_course_seats",
        constraints = "available_seats >= 0 AND available_seats <= capacity")
```
No new columns, entities or repositories, and no change to `data.sql` (all seeded rows satisfy the check). The existing `@Version` on `Course` stays. Existing tests that create courses (`RETRY1` 50/50, concurrency courses seats/seats, `PF0001`… 50/50) already satisfy the check.

Invariant (documented, tested by FR6-EX4/EX5): for every course, `availableSeats + registrations = capacity`.

### 3.2 Service layer
```java
// package edu.tku.registration.service
public class CourseFullException extends RuntimeException {
    public CourseFullException(String courseCode);   // getMessage() = "Course Full: " + courseCode + " has no available seats."
}
```
`RegistrationService.register(String studentId, Long courseId)` keeps its signature, transaction design, `MAX_ATTEMPTS = 5` and its step order (student, course, duplicate, seats guard, seat update, registration, audit). Two changes:
1. Step 4 stays `if (course.getAvailableSeats() <= 0) throw new CourseFullException(course.getCourseCode())`. It runs before any write, so a rejected attempt writes no registration, no audit and no seat change. A rejection (any exception that is not a `ConcurrencyFailureException`) is never retried.
2. After the 5th failed attempt (all with `ConcurrencyFailureException`), before throwing `RegistrationConflictException`, the service does one read-only check of the course: `courseRepository.findById(courseId)` in a new read-only transaction. If the course exists and `availableSeats <= 0`, it throws `CourseFullException(courseCode)`. Otherwise it throws `RegistrationConflictException` as before. (A `null` `courseId` never reaches this point.) The check does not call `saveAndFlush`, so FR3-EX10 and FR3-EX11 call counts do not change.

The conflict message `The course was just updated by another student. Please try again.` is now returned only when the course still has seats after 5 conflicting attempts.

### 3.3 Web layer
No new endpoints and no new classes. `ApiExceptionHandler` already maps `CourseFullException` to 409 `{"message": ex.getMessage()}`, so the new text flows through. The error body does not get a new key and stays exactly `{"message": "..."}` (see Assumption 1).

| Method | Path | Request | Success | Failures (status + JSON) |
|--------|------|---------|---------|--------------------------|
| POST | /api/registrations | `{"courseId": 1}` | 201 (unchanged from FR3) | **409 `Course Full: {code} has no available seats.`** (changed from `Course {code} is full.`); the other FR3 failures are unchanged |

`GET /api/courses` is unchanged. The `availableSeats` and `capacity` fields already tell the client whether a course is full (`availableSeats <= 0`).

Authentication required: yes, unchanged (NFR2). `POST /api/registrations` needs `ROLE_STUDENT` and the CSRF token. The student id comes from the security context.

### 3.4 User-facing messages (exact text)
Backend (JSON `message`):
| Key | Text |
|-----|------|
| register.full (changed) | Course Full: {courseCode} has no available seats. |
| register.conflict (unchanged) | The course was just updated by another student. Please try again. |

Frontend:
| Key | Text |
|-----|------|
| courses.full.tag | Full |
| courses.full.note | No seats available. |
| courses.register.error.4xx | unchanged: the server's `ApiError.message`, shown as is |

### 3.5 Frontend structure
Only `src/pages/CoursesPage.tsx` changes. No new files, no API changes (`api/courses.ts` and `api/types.ts` are untouched).

Rule: a course is *full* when `availableSeats <= 0`. The existing `seatColor` already uses this rule for the red seat tag.

Seats cell: keep the existing seat tag and the `course-seats-{id}` span (exact digits only). For a full course, add an antd `Tag` (color `red`) with text `Full` and `data-testid="full-tag-{id}"` next to it, outside the `course-seats-{id}` span, so that span's text is still just the number.

Action cell: the Register button is disabled when `registered || pending || full`. Loading and the label stay the same. When the course is full and not registered, render `<span data-testid="full-note-{id}" class="full-note">No seats available.</span>` under the button. The `Registered` tag (FR3) still shows when `registered` is true, with no `full-note`. For a full course the click handler is not reachable (the button is disabled).

Race handling needs no new code path. A click on an enabled button that the server rejects with 409 shows the server message in `register-error` and refetches (FR3-UI4 flow), after which the row renders in its full state. The Success handler already sets `availableSeats` from the response, so a registration that takes the last seat produces the full state (FR6-UI5).

### 3.6 UI elements tests rely on
All FR3 test ids stay. New ones:
| data-testid | Element | Content / notes |
|---|---|---|
| full-tag-{id} | antd `Tag` (red) | text `Full`. Rendered only when `availableSeats <= 0` (also for registered users). |
| full-note-{id} | `<span>` under the Register button | text `No seats available.` Rendered only when `availableSeats <= 0` and not `registered`. |
| register-btn-{id} | `<button>` | now also disabled when the course is full |

## 4. Assumptions & decisions
1. **The "Course Full" message.** The text is `Course Full: {code} has no available seats.` It literally contains the requirement's "Course Full" and still names the course. The JSON body stays exactly `{"message": "..."}`. I considered adding `code: "COURSE_FULL"` and rejected it. It would change the shape of every error assertion made through `jsonError` (strict compare), nothing in the UI needs it (the UI shows the message as is and never branches on it), and it can be added later without breaking anyone. If the human prefers FR3's old sentence plus a code field, the change is limited to `CourseFullException`, §3.3 and the tests in §6.
2. **UI.** FR3-UI7 is **replaced**. A full course now has a disabled Register button, a red `Full` tag and the note `No seats available.` The server remains the final authority: a stale page can still send the request, the server rejects with 409 Course Full, the message is shown and the list is refetched (FR3-UI4 flow, unchanged). A user who is registered on a full course sees `Registered` and `Full`, with no note.
3. **"Full" definition.** `availableSeats <= 0`, in both the service guard and the UI. The `Full` tag does not depend on `capacity`.
4. **FR6-AC3, no record.** A rejected attempt creates no `Registration`, no `AuditLog` and no seat or version change. This is tested at service and HTTP level. Rejected attempts are **not** audited (same as FR3 Assumption 14 and FR3-EX9). NFR4 logs registrations and drops only.
5. **Concurrency.** The existing optimistic lock (`@Version`) plus the retry loop already guarantees one winner. FR6 makes the outcome precise: every loser gets `CourseFullException` (FR3 allowed `RegistrationConflictException` too). With one seat there is exactly one committed seat change, so each loser conflicts at most once and then re-reads a full course. The final read-only check after exhausted retries (§3.2) covers any remaining interleaving and lock-timeout case, so FR6-AC3c can assert `CourseFullException` strictly. FR3-EX12/13/14 keep their looser assertions and are not changed.
6. **Conflict message vs Course Full.** `RegistrationConflictException` (`The course was just updated…`) is thrown only when the course still has seats after 5 conflicting attempts. If the course is full at that point, the student gets Course Full.
7. **Data integrity.** A Hibernate `@Check` gives a DB-level `CHECK (available_seats >= 0 AND available_seats <= capacity)`. It is a second line of defence behind the service guard and `@Version`. It is checked for the JDBC and JPA update paths. There is **no** DB-level check that seats equal capacity minus registrations. That cannot be a column check. It is asserted as a test invariant on seed data and after the race tests (FR6-EX4/EX5). A capacity edit by an admin is out of scope.
8. **Seed data unchanged.** MA101 (capacity 1, 0 seats, Carol registered) already satisfies the invariant. Tests that need other full courses (`ZERO1` capacity 5 / seats 0, `LAST1` capacity 1 / seats 1) and extra students create and remove them, so SK-AC3 counts stay `3 / 2 / 4 / 1 / 2 / 0`. Tests that write must be rolled back or use `@DirtiesContext(classMode = AFTER_CLASS)`.
9. **Test classes for concurrency and spies** are not `@Transactional` (threads need their own transactions) and follow the FR3 test-data rules. Extra students are created through `StudentRepository` with a BCrypt hash and deleted afterwards, or the class uses `@DirtiesContext`.
10. **Check order** is unchanged: student, course, duplicate, full. A student already registered for a full course sees `already registered` (FR3-AC4b).
11. **No waitlist, no seat reservation, no capacity increase.** Not part of FR6.
12. **Accessibility of the explanation.** The note is plain visible text next to the disabled button (not a tooltip, which needs hover and is hard to test on a disabled button).

## 5. Out of scope
- Registration period checks (FR7), drop and seat release (FR4), schedule page (FR5).
- Waitlists, seat reservations, admin capacity changes, real-time seat updates.
- A machine-readable error `code` field (Assumption 1), and audit of rejected attempts (Assumption 4).
- Any change to `GET /api/courses`, seed data, or non-registration endpoints.

## 6. Changes to existing FR3 criteria and tests
Everything not listed here keeps passing unchanged. The tester updates tests, and the coder never edits `src/test/`.

| Existing item | Change |
|---|---|
| FR3 spec §3.2, §3.4 (`CourseFullException`, `register.full`) and Assumption 8 | The text `Course {code} is full.` is replaced by `Course Full: {code} has no available seats.` The "minimal guard, FR6 will refine" notes are superseded by this spec. |
| **FR3-EX1** (`RegistrationControllerTest`, `@DisplayName` "FR3-EX1 ... 'Course MA101 is full.' ..."; the assertion at the line `register(BOB, ma101).andExpect(jsonError(409, "Course MA101 is full."))`) | Must change. The expected message becomes `Course Full: MA101 has no available seats.` The rest of FR3-EX1 (seats stay 0, no registration, no audit) is unchanged. The `@DisplayName` should still start with `FR3-EX1`. |
| **FR3-UI7** (`CoursesPage.test.tsx`, "a full unregistered course keeps an enabled Register button...") | Must be **removed or replaced**. Its behaviour is reversed. It is replaced by FR6-UI1 and FR6-UI2 (disabled button, no API call). |
| FR3-UI4 and FR3-UI6 (mock rejections with `"Course CS101 is full."`) | Still pass, because the mock text is arbitrary. The tester should update those strings to `Course Full: CS101 has no available seats.` for consistency (optional). |
| FR3-EX9, FR3-EX11, FR3-EX12/13/14, NFR4-AC1 | Unchanged. The FR3-EX11 conflict text still applies when the course still has seats. |
| FR2-UI2 (`course-seats-3` is `0`) | Unchanged. The `Full` tag is outside the `course-seats-{id}` span. |
| FR3-UI2 (MA101 registered: button disabled, `registered-tag-3`) | Unchanged. It now also shows `full-tag-3`, and no test asserts its absence. |
| NFR5-AC1/AC2 | Unchanged (CS101 has seats). |
