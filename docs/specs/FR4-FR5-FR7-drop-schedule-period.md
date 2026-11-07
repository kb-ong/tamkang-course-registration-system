# Spec: FR4-FR5-FR7-drop-schedule-period — Drop Course, Registered Courses (schedule, credits, print/CSV) and Registration Period Control

Requirements covered: FR4 (AC1–AC5), FR5 (AC1–AC3), FR7 (AC1–AC3), NFR4 (audit of drops), and the parts of NFR2 (new endpoints need authentication and CSRF) and NFR5 (click counts for drop) that they touch.
Status: Draft (approved at the /build-feature human gate)
Builds on: `docs/specs/FR1-login.md`, `docs/specs/FR2-FR3-courses-register.md`, `docs/specs/FR6-capacity.md`. Where this spec contradicts them, this spec wins, and §6 lists every earlier criterion and test that changes.

## 1. Summary
Registration (and dropping) is only possible while the course's semester has an open registration period (inclusive bounds, Asia/Taipei, injected `Clock`). Outside it the server rejects with 409 and the UI shows an Open/Closed banner on `/courses` and disables **Register** with a visible reason. The dashboard gets a **My registered courses** card: a semester selector (current semester by default), a table of the student's registrations with a server-computed **total credits** figure, a **Drop** button per row (inline two-step confirm, no dialog), a **Print** button and a **Download CSV** button. Dropping deletes the registration, gives the seat back, writes a `DROP` audit row, all in one transaction. After the period ends the registered courses stay visible, Drop is disabled with a reason.

**Build slices.** Slice A, backend: §3.1–§3.4 and all *backend* criteria. Slice B, frontend: §3.5–§3.7 and all *frontend* criteria. Slice B depends only on the §3.3 JSON contract (its tests mock the API modules). Recommended: one run, two sequential passes, A then B (see §4 item 30). No further split is needed.

## 2. Acceptance criteria

Test levels:
- **backend**: JUnit 5 under `src/test/java`. HTTP tests use `@SpringBootTest` + `@AutoConfigureMockMvc` with real seed data, `spring-security-test` (`csrf()`, `user("410000001").roles("STUDENT")`) and `ApiTestSupport.jsonError(status, message)` (body exactly `{"message":"X"}`). Service and concurrency tests call beans directly. **Every backend test that registers or drops must pin the `Clock`** (see "Clock approach" below).
- **frontend**: Vitest + React Testing Library under `frontend/src/__tests__/`. API modules are mocked with `vi.mock` (`../api/auth`, `../api/dashboard`, `../api/courses`, `../api/registrations`, `../utils/download`). The `it` name starts with the criterion ID.
- **unit**: a pure function tested with Vitest, no React.
- **manual**: not automated, checked by a human in a browser.

Seed facts (unchanged seed): semester `115-1` "2026 Fall" (current), period `2026-08-01T00:00:00` to `2026-12-31T23:59:59`; semester `114-2` "2026 Spring", period `2026-01-15T00:00:00` to `2026-02-28T23:59:59`. `115-1` courses: CS101 (3 credits, 50/50 seats), CS201 (3, 40/40), MA101 (4, capacity 1, 0 seats). `114-2`: CS102 (3, 45/45). Carol (`410000003`) is registered for MA101 (registered `2026-08-10T10:00:00`). Alice is `410000001`, Bob `410000002`.

**Clock approach (all period tests).** The tester provides a mutable fixed `Clock` as a `@Primary` test bean (like `RegistrationControllerTest.MutableClock`, zone `Asia/Taipei`) and sets it per test. Named instants used below:
- `T_OPEN = 2026-09-01T09:00:00+08:00` (inside `115-1`, outside `114-2`)
- `T_START = 2026-08-01T00:00:00+08:00`, `T_BEFORE_START = 2026-07-31T23:59:59+08:00`
- `T_END = 2026-12-31T23:59:59+08:00`, `T_END_MS = 2026-12-31T23:59:59.999+08:00`, `T_AFTER_END = 2027-01-01T00:00:00+08:00`
- `T_SPRING = 2026-02-01T09:00:00+08:00` (inside `114-2`, outside `115-1`)
- `T_LATE = 2027-06-01T09:00:00+08:00` (outside both)

### FR7: Registration Period Control (backend)
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR7-AC1a | Inside the period, inclusive bounds. For Alice and CS101 (`115-1`): at `T_START`, at `T_OPEN`, at `T_END` and at `T_END_MS`, `POST /api/registrations {"courseId": cs101Id}` returns 201 (each case in its own rolled-back test). The bound is inclusive at both ends, and the end second includes its sub-second part (`23:59:59.999` is open). | mvc |
| FR7-AC1b | Outside the period. For Alice and CS101: at `T_BEFORE_START`, at `T_AFTER_END` and at `T_LATE`, the POST returns 409 `jsonError(409, "Registration is closed for semester 115-1.")`. CS101 `availableSeats` (50) and `version` are unchanged, there is no `Registration` row for the pair and no new `AuditLog` row. `RegistrationService.register("410000001", cs101Id)` throws `RegistrationClosedException` with that message. | mvc + service |
| FR7-AC1c | The period of the **course's own semester** decides, not the current semester. At `T_OPEN`, CS102 (`114-2`) is rejected with 409 `Registration is closed for semester 114-2.` At `T_SPRING`, registering CS102 returns 201 (message `Successfully registered for CS102.`, `availableSeats` 44), and CS101 (`115-1`) is rejected with `Registration is closed for semester 115-1.` | mvc |
| FR7-EX1 | No period configured means closed. In a rolled-back test, create `Semester("NOPD-1","No Period Semester",false)` and a course `NP101` in it (capacity 5, seats 5) with no `RegistrationPeriod`. At any clock value, registering returns 409 `Registration is closed for semester NOPD-1.` and nothing is written. | mvc |
| FR7-EX2 | Past and future periods. In a rolled-back test, create semesters `PAST-1` (period `2025-01-01T00:00:00`–`2025-01-31T23:59:59`) and `FUT-1` (period `2027-01-01T00:00:00`–`2027-01-31T23:59:59`), each with a course. At `T_OPEN`, registering in either returns 409 `Registration is closed for semester {code}.` With the clock moved inside each period (`2025-01-15T12:00+08:00`, `2027-01-15T12:00+08:00`), registering returns 201. | mvc |
| FR7-EX3 | Check order is student, course, **period**, duplicate, full. At `T_LATE`: (a) Carol posting MA101 (already registered and full) gets 409 `Registration is closed for semester 115-1.` (the period check comes before duplicate and full); (b) a nonexistent course id still gives 404 `Course not found.`; (c) an unknown student (`user("499999999")`) still gives 404 `Student not found.` At `T_OPEN`, Carol posting MA101 still gets 409 `You are already registered for MA101.` (FR3-AC4b unchanged). | mvc |
| FR7-EX4 | `SemesterService` unit behaviour with `Clock.fixed(..., Asia/Taipei)`, constructed as `new SemesterService(semesterRepository, registrationPeriodRepository, clock)` (the constructor is unchanged). `isRegistrationOpen("115-1")` is `true` at local `2026-08-01T00:00:00`, `2026-12-31T23:59:59` and `2026-12-31T23:59:59.999999999`, and `false` at `2026-07-31T23:59:59.999` and `2027-01-01T00:00:00`. `isRegistrationOpen("114-2")` is `false` at `T_OPEN`. `isRegistrationOpen("NOPE-9")` (unknown semester) is `false`. A semester without a period gives `getRegistrationPeriod(code)` = `RegistrationPeriodInfo(null, null, false)`. The existing FR1-EX17 criteria still pass. | service |
| FR7-EX5 | `GET /api/courses` carries the selected semester's period status. As Alice: at `T_OPEN` the body has `registrationPeriod` = `{"startAt":"2026-08-01T00:00:00","endAt":"2026-12-31T23:59:59","open":true}`; at `T_LATE` the same dates with `"open":false`; with `?semester=114-2` at `T_OPEN`: `{"startAt":"2026-01-15T00:00:00","endAt":"2026-02-28T23:59:59","open":false}`. When no semester is selected (no current semester, no param; `@Transactional` service test as FR2-EX5) `registrationPeriod` is `null`. The course objects keep exactly the keys of FR2-AC2 and `semester` keeps exactly `code`, `name`, `current`. | mvc + service |
| FR7-EX6 | Rejected-by-period attempts are never audited: after FR7-AC1b, FR7-EX1, FR7-EX2 and FR7-EX3 the `audit_log` row count equals its value before. | mvc |
| FR7-EX7 | The dashboard and the enforcement use the same rule. At `T_END_MS`, `GET /api/dashboard` has `currentSemester.registrationOpen` `true`, and at `T_AFTER_END` `false` (the FR1-AC3 example at `2026-09-01T09:00` is unchanged). | mvc |
| FR7-AC3a | Existing registrations stay viewable after the period ends. At `T_LATE`, `GET /api/registrations` as Carol returns 200 with MA101 in `courses` and `registrationPeriod.open` `false`. `GET /api/courses` as Carol still returns `registered: true` for MA101. | mvc |

### FR7: Registration Period Control (frontend)
Common setup for FR7-UI1…UI8: `fetchMe()` resolves Alice, `fetchDashboard()` the FR1 example, `fetchSemesters()` the two semesters, and the app is rendered at `/courses`. `fetchCourses` resolves the existing *listing fixture* (CS101 id 1 50/50 unregistered, CS201 id 2 40/40 unregistered, MA101 id 3 0 seats unregistered unless stated) extended with `registrationPeriod`. `OPEN_PERIOD = {startAt:"2026-08-01T00:00:00", endAt:"2026-12-31T23:59:59", open:true}`, `CLOSED_PERIOD` is the same with `open:false`.

| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR7-UI1 | Given `registrationPeriod: OPEN_PERIOD`, then `[data-testid="period-banner"]` has attribute `data-status="open"` and exact text `Registration is open. Period: 2026-08-01 00:00 to 2026-12-31 23:59.`, and `register-btn-1` is enabled. `closed-note-1` is not in the document. | frontend |
| FR7-UI2 | Given `registrationPeriod: CLOSED_PERIOD`, then `period-banner` has `data-status="closed"` and exact text `Registration is closed. Period: 2026-08-01 00:00 to 2026-12-31 23:59.`; `register-btn-1` and `register-btn-2` are disabled and show text `Register`; `[data-testid="closed-note-1"]` and `closed-note-2` have exact text `Registration is closed.`; clicking `register-btn-1` does not call `registerForCourse`. Seats and the course table are still shown. | frontend |
| FR7-UI3 | Given `registrationPeriod: {startAt:null, endAt:null, open:false}`, then `period-banner` has `data-status="closed"` and exact text `Registration is closed. No registration period is scheduled for this semester.` and the Register buttons are disabled. | frontend |
| FR7-UI4 | Given `CLOSED_PERIOD` and MA101 (`registered: true`), then `registered-tag-3` (text `Registered`) is in the document, `register-btn-3` is disabled, and `closed-note-3` is **not** in the document (the registered tag explains it). | frontend |
| FR7-UI5 | Switching semester updates the banner. Given the first listing has `OPEN_PERIOD` and, after the user selects `114-2`, `fetchCourses` resolves the spring listing with `{startAt:"2026-01-15T00:00:00", endAt:"2026-02-28T23:59:59", open:false}`, then the banner shows `data-status="closed"` and the text `Registration is closed. Period: 2026-01-15 00:00 to 2026-02-28 23:59.`, and the Register button of CS102 is disabled. | frontend |
| FR7-UI6 | Compatibility default. Given a listing with **no** `registrationPeriod` key (the FR3/FR6 fixtures), then `period-banner` is not in the document and Register buttons follow the FR3/FR6 rules (enabled for CS101). Given `registrationPeriod: null` with `semester: null`, there is no banner. | frontend |
| FR7-UI7 | Server rejection refreshes the banner. Given `OPEN_PERIOD`, when `registerForCourse` rejects with `new ApiError(409, "Registration is closed for semester 115-1.")` and the refetch resolves `CLOSED_PERIOD`, then `register-error` has text `Registration is closed for semester 115-1.`, the banner switches to `data-status="closed"` and `register-btn-1` becomes disabled. The error stays visible after the refetch. | frontend |
| FR7-UI8 | The banner is shown while the table is empty. Given the listing has `courses: []`, a semester and `CLOSED_PERIOD`, then `courses-empty` has `No courses found.` and the closed banner is shown as well. | frontend |

### FR4: Drop Course (backend)
Setup for every FR4 MockMvc test unless stated: authenticated as `user("410000001").roles("STUDENT")` (or the named student), a valid CSRF token, `Clock` = `T_OPEN`, rolled-back `@Transactional` test. `DELETE /api/registrations/{courseId}` has no body. Alice has registered CS101 first in the test (`POST`, 201), so before the drop CS101 has 49 seats. Let `cs101Id` be its id.

| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR4-AC1 | A student can view the registered courses to choose what to drop: after Alice registers CS101, `GET /api/registrations` contains CS101 (see FR5-AC1). The frontend entry point is FR4-UI1. | mvc |
| FR4-AC2 | Remove. `DELETE /api/registrations/{cs101Id}` returns 200 and a body with exactly the keys `message`, `courseId`, `courseCode`, `availableSeats`: `message` `Successfully dropped CS101.`, `courseId` = `cs101Id`, `courseCode` `CS101`, `availableSeats` 50. There is no `Registration` for (Alice, CS101) any more. A second `DELETE` returns 404 (FR4-EX1). | mvc |
| FR4-AC3 | Seat +1. After FR4-AC2, CS101 in the database has `availableSeats` 50 (was 49) and its `version` is greater than before. CS201 and MA101 are unchanged. Carol dropping MA101 (at `T_OPEN`) takes MA101 from 0 to 1 seat (capacity 1). The seat invariant `availableSeats + registrations = capacity` holds for CS101 and MA101 afterwards. | mvc |
| FR4-AC4 | Removed from the schedule. After FR4-AC2, `GET /api/registrations` as Alice has `courses: []` and `totalCredits` 0, and `GET /api/courses` shows CS101 with `registered: false`. | mvc |
| FR4-AC5 | Confirmation message. The message of the 200 body is exactly `Successfully dropped CS101.` (`Successfully dropped {courseCode}.`). | mvc |
| FR4-AC6 | Audit (NFR4). After a successful drop there is exactly one new `AuditLog` row with `studentId` `410000001`, `courseId` = `cs101Id`, `action` `DROP` and `timestamp` `2026-09-01T09:00:00` (the fixed clock). The earlier `REGISTER` row is still there (2 rows for the course in total). | mvc |
| FR4-EX1 | Not registered. Bob (no registration) sends `DELETE /api/registrations/{cs101Id}`, then 404 `jsonError(404, "You are not registered for CS101.")`. Seats, version, `registration` and `audit_log` counts are unchanged. | mvc |
| FR4-EX2 | Course not found / malformed id. `DELETE /api/registrations/999999`, `/0` and `/-5` each give 404 `Course not found.` and write nothing. `DELETE /api/registrations/abc` gives 400 `Malformed request.` (not 500). | mvc |
| FR4-EX3 | Outside the period the drop is refused. Carol drops MA101 at `T_LATE`, at `T_BEFORE_START` and at `T_AFTER_END`: 409 `Dropping is closed for semester 115-1.` The registration row, MA101 seats (0), version and the audit count are unchanged. The same applies to a course in a semester without a period (`NOPD-1`, as FR7-EX1, with a registration inserted by the test): 409 `Dropping is closed for semester NOPD-1.` At `T_START`, `T_END` and `T_END_MS` the drop succeeds (inclusive bounds). `RegistrationService.drop` throws `DropClosedException` with that message. | mvc + service |
| FR4-EX4 | Ownership. The student id comes only from the security context, and the course id is the only input. Alice sends `DELETE /api/registrations/{ma101Id}` (Carol's registration): 404 `You are not registered for MA101.` Carol's registration, MA101 seats (0) and the audit count are unchanged. | mvc |
| FR4-EX5 | NFR2/CSRF. Anonymous `DELETE /api/registrations/{id}` with a valid CSRF token gives 401 `Authentication required.` An authenticated `DELETE` **without** a CSRF token gives 403 `Invalid or missing CSRF token.` With the real cookie flow (cookie `XSRF-TOKEN=v` and header `X-XSRF-TOKEN: v`) it succeeds. Nothing is written in the two failures (row counts of `registration` and `audit_log`, MA101 seats). | mvc |
| FR4-EX6 | Atomicity. With `AuditLogRepository` as a `@MockitoSpyBean` whose `save` throws `RuntimeException`, `RegistrationService.drop("410000001", id)` propagates the exception (HTTP 500 `An unexpected error occurred.`), the registration row still exists and the course seats are unchanged. Not `@Transactional`; the test creates its own course and cleans up. | service |
| FR4-EX7 | Optimistic-lock retry. With `CourseRepository` as a `@MockitoSpyBean` whose first `saveAndFlush(any(Course))` throws `ObjectOptimisticLockingFailureException` and later calls run the real method, `drop(...)` succeeds, seats rise by exactly 1, the registration is gone, there is exactly one `DROP` audit row, and `saveAndFlush(Course)` was called twice. Not `@Transactional`. | service |
| FR4-EX8 | Exhausted retries. With every `saveAndFlush(any(Course))` throwing `ObjectOptimisticLockingFailureException`, `drop` throws `RegistrationConflictException` (message `The course was just updated by another student. Please try again.`) after exactly 5 calls; the registration still exists, seats are unchanged, no audit row. | service |
| FR4-EX9 | Concurrency, double submit. Given Alice is registered for a test course with capacity 50 (seats 49), when Alice calls `drop` for it simultaneously from two threads (released together by a latch), then exactly one returns, and the other throws `NotRegisteredException` or `RegistrationConflictException`. Afterwards seats are 50 (incremented once), there are 0 registrations and exactly one `DROP` audit row. Repeat for 20 rounds. | service |
| FR4-EX10 | Concurrency, drop racing register on the last seat. Given a test course with capacity 1 and 0 seats and Alice registered, when Alice's `drop` and Bob's `register` run simultaneously from two threads, then in every outcome: seats + registrations = capacity, seats is 0 or 1 (never negative, never above capacity), exactly one `DROP` audit row exists, the number of `REGISTER` audit rows (Alice's earlier one included) equals 1 + (1 if Bob's call returned), Bob's call either returns or throws `CourseFullException` or `RegistrationConflictException`, and Alice's call returns. Repeat for 20 rounds. | service |
| FR4-EX11 | Never above capacity. Given a test course with capacity 2 and 2 seats and a `Registration` for Alice inserted directly through `RegistrationRepository` (an inconsistent state), when Alice drops, then it returns normally, the registration is deleted, and `availableSeats` is still 2 (not 3, and no `DataIntegrityViolationException`). | service |
| FR4-EX12 | Re-register after a drop. Alice registers CS101 (201), drops it (200) and registers again (201) at `T_OPEN`. Seats go 50, 49, 50, 49. The audit rows for CS101 are, in order, `REGISTER`, `DROP`, `REGISTER`. | mvc |
| FR4-EX13 | A rejected drop never writes an audit row (not registered, closed, not found): after FR4-EX1, EX2, EX3 and EX4 the `audit_log` count equals its starting value. | mvc |
| FR4-EX14 | Check order is student, course, **period**, registration. At `T_LATE`, Bob (not registered) dropping CS101 gets 409 `Dropping is closed for semester 115-1.`; at `T_OPEN` he gets 404 `You are not registered for CS101.` An unknown student still gets 404 `Student not found.` | mvc |
| FR4-EX15 | Semester of the course decides. Alice registers CS102 at `T_SPRING` and drops it at `T_SPRING` (200, seats back to 45). Dropping CS102 at `T_OPEN` gives 409 `Dropping is closed for semester 114-2.` | mvc |
| NFR4-AC2 | Every successful drop writes exactly one `DROP` `AuditLog` row, in the same transaction as the registration delete and the seat change (see FR4-AC6, FR4-EX6). Failed drops write none (FR4-EX13). | mvc + service |

### FR5: View Registered Courses (backend)
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR5-AC1 | `GET /api/registrations` as Carol (Clock `T_OPEN`) returns 200 with exactly the top-level keys `semester`, `semesters`, `registrationPeriod`, `courses`, `totalCredits`. `semester` is `{"code":"115-1","name":"2026 Fall","current":true}`. `courses` is exactly one object with exactly the keys `registrationId`, `courseId`, `code`, `name`, `credits`, `instructor`, `semesterCode`, `registeredAt`: `code` `MA101`, `name` `Calculus I`, `credits` 4, `instructor` `Prof. Huang`, `semesterCode` `115-1`, `registeredAt` `2026-08-10T10:00:00`, `courseId` = MA101's id, and `registrationId` a number. As Alice (no registrations) `courses` is `[]`. | mvc |
| FR5-AC2 | Total credit hours, computed by the service. After Alice registers CS101 (3 credits) and CS201 (3), `totalCredits` is 6 and `courses` has 2 rows. After she drops CS201 it is 3. For Carol it is 4. With no registrations it is 0 (JSON number, not null). `ScheduleService.getSchedule` returns the same totals when called directly. The total sums `credits` of exactly the rows listed (the selected semester only). | mvc + service |
| FR5-EX1 | Semester selection. No `semester` param selects the current semester (`115-1`). `?semester=114-2` selects `114-2`. After Alice registers CS102 at `T_SPRING` and CS101 at `T_OPEN`: default listing (at any clock) has only CS101 and `totalCredits` 3, `?semester=114-2` has only CS102 and `totalCredits` 3. `?semester=999-9` gives 404 `Semester not found.` A blank `semester` is treated as absent. | mvc |
| FR5-EX2 | No current semester. In a `@Transactional` service test with `115-1` set to `current=false` and flushed, `getSchedule("410000001", null)` returns `semester == null`, `registrationPeriod == null`, `courses` empty, `totalCredits` 0, and `semesters` still lists both semesters. `getSchedule("410000003", "115-1")` still returns Carol's MA101. | service |
| FR5-EX3 | `registrationPeriod` is the selected semester's period status (same shape as FR7-EX5): for `115-1` at `T_OPEN` `{"startAt":"2026-08-01T00:00:00","endAt":"2026-12-31T23:59:59","open":true}`; at `T_LATE` `open` false; for `114-2` at `T_OPEN` `open` false. | mvc |
| FR5-EX4 | `semesters` lists all semesters ordered by code descending: `[{"code":"115-1","name":"2026 Fall","current":true},{"code":"114-2","name":"2026 Spring","current":false}]`. | mvc |
| FR5-EX5 | Isolation and order. A student only sees their own registrations: Alice's response never contains MA101 (Carol's). Rows are ordered by `code` ascending (CS101 before CS201). `registeredAt` is `yyyy-MM-dd'T'HH:mm:ss` and equals the fixed clock when registered at `T_OPEN` (`2026-09-01T09:00:00`). | mvc |
| FR5-EX6 | No entity leakage: the JSON never contains `version`, `passwordHash`, `student`, `email`, or `capacity`. | mvc |
| FR5-EX7 | NFR2. Anonymous `GET /api/registrations` (with and without `?semester=114-2`) returns 401 `Authentication required.` (the existing NFR2-AC2 keeps passing now that the path has a controller). An unknown student (`user("499999999")`) gets 404 `Student not found.` | mvc |

### FR4 / FR5: Frontend (Vitest)
Common setup unless stated: `fetchMe()` resolves Alice, `fetchDashboard()` resolves the FR1 example, and the app is rendered at `/dashboard`. `fetchRegistrations` (from `../api/registrations`, mocked) resolves the **schedule fixture**:
```
semester {code:"115-1", name:"2026 Fall", current:true}
semesters [{115-1 current}, {code:"114-2", name:"2026 Spring", current:false}]
registrationPeriod OPEN_PERIOD
courses [
 {registrationId:10, courseId:1, code:"CS101", name:"Introduction to Programming", credits:3, instructor:"Prof. Lee",   semesterCode:"115-1", registeredAt:"2026-09-01T09:00:00"},
 {registrationId:11, courseId:2, code:"CS201", name:"Data Structures",             credits:3, instructor:"Prof. Chang", semesterCode:"115-1", registeredAt:"2026-09-02T10:30:00"} ]
totalCredits 6
```
The section is rendered only after the dashboard itself loaded (§3.5).

| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR5-UI1 | The dashboard displays registered courses (FR5-AC1). Given the schedule fixture, then `[data-testid="registered-card"]` has the title `My registered courses`, `fetchRegistrations` was called once with `undefined` (no argument), `[data-testid="registered-table"]` shows the headers `Code`, `Name`, `Credits`, `Instructor`, `Registered On`, `Action`, and the rows `registered-row-1` and `registered-row-2` in that order. For course id 1, the exact text of `registered-code-1` is `CS101`, `registered-name-1` `Introduction to Programming`, `registered-credits-1` `3`, `registered-instructor-1` `Prof. Lee`, and `registered-date-1` `2026-09-01 09:00`. The profile and semester cards are still shown. | frontend |
| FR5-UI2 | Total credits (FR5-AC2). Given the schedule fixture, then `[data-testid="total-credits"]` has exact text `6` (the value is `totalCredits` from the API, not computed by the UI: a fixture with `totalCredits: 99` and rows of 3+3 shows `99`), and its label reads `Total credit hours:`. With `courses: []` and `totalCredits: 0` it shows `0`. | frontend |
| FR5-UI3 | States. While `fetchRegistrations` is pending, `registered-loading` is shown. With `courses: []` and a semester, `[data-testid="registered-empty"]` has text `You have not registered for any courses in this semester.` and no `registered-table`. With `semester: null`, `registered-empty` has text `No semester has been selected.` When it rejects with `new ApiError(500, "An unexpected error occurred.")` (or a non-ApiError), `[data-testid="registered-error"]` has text `Unable to load your registered courses. Please try again later.`, and the profile card is still shown. When it rejects with `new ApiError(401, "Authentication required.")`, `login-card` appears. | frontend |
| FR5-UI4 | Semester selector. `[data-testid="registered-semester-select"]` is a native `<select>` labelled `Semester` (`getByLabelText("Semester")` resolves to it within the card) with options `2026 Fall (115-1)` and `2026 Spring (114-2)` (from the response's `semesters`), value `115-1`. When `114-2` is selected, `fetchRegistrations` is called with `"114-2"`, and the table shows the new response. The drop feedback alert is cleared on a semester change. | frontend |
| FR5-UI5 | Print (FR5-AC3). `[data-testid="print-btn"]` is a button with text `Print`. Clicking it calls `window.print` (a `vi.fn()` assigned to `window.print`) exactly once. The card has the class `print-area`. A `[data-testid="print-header"]` element (class `print-only`) contains the text `Alice Chen (410000001)`, `2026 Fall (115-1)` and `Total credit hours: 6`. | frontend |
| FR5-UI6 | CSV download (FR5-AC3). With `../utils/download` mocked, clicking `[data-testid="csv-btn"]` (text `Download CSV`) calls `downloadTextFile` once with `("registered-courses-115-1.csv", <string>)` where the string equals `buildScheduleCsv(courses, totalCredits)` for the fixture (the unit test FR5-UI7 pins its format). | frontend |
| FR5-UI7 | CSV format, pure function `buildScheduleCsv(courses: RegisteredCourse[], totalCredits: number): string` from `src/utils/scheduleCsv.ts`. (a) For the fixture the result is exactly the 4 lines joined by `\r\n` (no trailing newline): `Course Code,Course Name,Credits,Instructor,Semester,Registered At` / `CS101,Introduction to Programming,3,Prof. Lee,115-1,2026-09-01 09:00` / `CS201,Data Structures,3,Prof. Chang,115-1,2026-09-02 10:30` / `Total Credits,,6,,,`. (b) A field containing a comma, a double quote, CR or LF is wrapped in double quotes with embedded quotes doubled: name `Algebra, Part "I"` becomes `"Algebra, Part ""I"""`. (c) A text field (code, name, instructor) that starts with `=`, `+`, `-`, `@` or a tab gets a leading `'` before escaping (`=SUM(1)` becomes `'=SUM(1)`). Numbers are never prefixed. (d) With no courses the result is the header line and `Total Credits,,0,,,` only. (e) `scheduleCsvFileName("115-1")` returns `registered-courses-115-1.csv`; a code is used as is. (f) `registeredAt` is formatted `YYYY-MM-DD HH:mm` (replace `T`, keep the first 16 characters). | unit |
| FR5-UI8 | Print and CSV are disabled when the list is empty (`print-btn` and `csv-btn` are disabled, `registered-empty` shown) and when `semester` is null. They are enabled with rows, **also when the period is closed** (FR7-AC3). | frontend |
| FR5-UI9 | API client, `fetch` stubbed, real `api/registrations` module. `fetchRegistrations()` calls `fetch("/api/registrations", init)` with method `GET`; `fetchRegistrations("114-2")` calls `"/api/registrations?semester=114-2"`; an empty string is omitted. `dropCourse(1)` calls `fetch("/api/registrations/1", init)` with `method` `"DELETE"`, `credentials` `"same-origin"`, `headers["X-XSRF-TOKEN"]` `"abc123"` (cookie `XSRF-TOKEN=abc123`) and no `body`. A 200 resolves the parsed JSON, and a 404 with `{"message":"X"}` rejects with `ApiError(404, "X")`. | frontend |
| FR5-MAN1 | Print layout (manual). In a real browser, press **Print** (or Ctrl+P) on `/dashboard`: the print preview shows only the registered-courses card, with the print header (name, student ID, semester, total credits), the table without the Action column, and the total. The header bar, sidebar, footer, profile and semester cards, the semester selector, the buttons and alerts are not printed. | manual |
| FR4-UI1 | Drop entry point (FR4-AC1/AC2). Given the schedule fixture (period open), then `drop-btn-1` and `drop-btn-2` are buttons with text `Drop` and are enabled. Clicking `drop-btn-1` shows `[data-testid="drop-confirm-text-1"]` with text `Drop CS101?`, `drop-confirm-1` (text `Confirm drop`) and `drop-cancel-1` (text `Cancel`), and `dropCourse` has **not** been called. Clicking `drop-cancel-1` hides them and shows `drop-btn-1` again, still without calling `dropCourse`. No element with role `dialog` and no `Popconfirm` or `Modal` is used. Opening the confirm on row 2 closes the one on row 1. | frontend |
| FR4-UI2 | Drop success (FR4-AC2…AC5). Given the fixture, when the user clicks `drop-btn-1` then `drop-confirm-1` and `dropCourse` resolves `{message:"Successfully dropped CS101.", courseId:1, courseCode:"CS101", availableSeats:50}`, then `dropCourse` was called once with exactly `(1)`, `[data-testid="drop-success"]` has text `Successfully dropped CS101.`, `fetchRegistrations` is called a second time (with the selected semester `"115-1"`) and, when it resolves a schedule with only CS201 and `totalCredits: 3`, `registered-row-1` is gone, `registered-row-2` stays and `total-credits` is `3`. The success alert stays visible after the refetch, and `drop-error` is not shown. | frontend |
| FR4-UI3 | Pending. While `dropCourse` is pending, `drop-confirm-1` is disabled (antd `loading`), and clicking it again does not call `dropCourse` a second time. Other rows' Drop buttons are unaffected. | frontend |
| FR4-UI4 | Errors. When `dropCourse` rejects with `new ApiError(404, "You are not registered for CS101.")` (or any other 4xx, for example `409` `Dropping is closed for semester 115-1.`), then `[data-testid="drop-error"]` shows that message, `drop-success` is not in the document, the confirm step closes, and `fetchRegistrations` is called again (second call with the selected semester) to refresh. When it rejects with a 5xx `ApiError` or a non-ApiError, `drop-error` has text `Unable to drop the course. Please try again later.` and no refetch happens. When it rejects with `new ApiError(401, "Authentication required.")`, `login-card` appears. | frontend |
| FR4-UI5 | Feedback is single. `drop-success` and `drop-error` are never shown together, and each is removed when the user starts another drop (clicks a `drop-btn-*`) or changes the semester. | frontend |
| FR7-UI9 | Registered courses stay visible, Drop disabled (FR7-AC3). Given the schedule fixture with `registrationPeriod: CLOSED_PERIOD`, then `registered-row-1` and `registered-row-2`, `total-credits` `6`, `print-btn` and `csv-btn` (enabled) are shown, `drop-btn-1` and `drop-btn-2` are disabled (text `Drop`), clicking them does not open the confirm and does not call `dropCourse`, and `[data-testid="drop-closed-note"]` has exact text `Dropping courses is only possible during the registration period.` With `OPEN_PERIOD` the note is not in the document. With `registrationPeriod: {startAt:null,endAt:null,open:false}` the note is shown too. With `registrationPeriod: null` (no semester) the note is not shown. | frontend |
| NFR5-AC3 | Fewer than 5 clicks to drop. Given a logged-in user at `/dashboard` with the common mocks, the flow "click `drop-btn-1`, click `drop-confirm-1`" (2 clicks) ends with `drop-success` showing `Successfully dropped CS101.` The test counts its `user.click` calls and asserts the count is less than 5. | frontend |
| NFR5-AC1 / NFR5-AC2 | Unchanged. Registering stays two clicks from the dashboard with no confirmation (the banner and `closed-note` add no click and no dialog when the period is open). | frontend |

## 3. Contract

### 3.1 Domain / data model
**No new entities, columns or constraints, and no change to `data.sql`.** `RegistrationPeriod` (one per semester, unique `semester_id`, inclusive `startAt`/`endAt`) and `AuditAction.DROP` already exist, and so does the `Course` check constraint `ck_course_seats`. A semester with no `RegistrationPeriod` row has **closed** registration (Assumption 2).

Repository additions (names are the coder's choice, tests must not call them directly):
- `RegistrationRepository`: find the `Registration` for (student pk, course id) (used by drop); a query of the registrations of a student (by `studentId` string) for a semester code, `join fetch` of course and course.semester, ordered by course code ascending (single query, no N+1).
- `SemesterRepository`: `findAllByOrderByCodeDesc` already exists.

Seed data: unchanged (SK-AC3 stays `3 / 2 / 4 / 1 / 2 / 0`). Tests that need more data create it and remove it, or roll back (§6.1).

### 3.2 Service layer
```java
// package edu.tku.registration.service
public record RegistrationPeriodInfo(
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startAt,   // null if no period
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endAt,     // null if no period
        boolean open) { }

@Service
public class SemesterService {   // constructor UNCHANGED: (SemesterRepository, RegistrationPeriodRepository, Clock)
    /** Existing; now computes `registrationOpen` with the same rule as isRegistrationOpen. */
    public Optional<CurrentSemesterInfo> getCurrentSemesterInfo();

    /** Read-only transaction (joins an existing one). Period of the given semester code. If the semester has no
     *  period (or the code is unknown): new RegistrationPeriodInfo(null, null, false).
     *  open = !now.isBefore(startAt) && !now.isAfter(endAt), where
     *  now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)  (inclusive bounds at whole seconds; the
     *  whole end second, e.g. 23:59:59.999, is still open). */
    public RegistrationPeriodInfo getRegistrationPeriod(String semesterCode);

    /** getRegistrationPeriod(semesterCode).open() */
    public boolean isRegistrationOpen(String semesterCode);
}
```
`getCurrentSemesterInfo()` must use the same truncated-now rule, so the dashboard and enforcement never disagree (FR7-EX7). It never calls `LocalDateTime.now()` directly (CLAUDE.md).

```java
public record DropResult(Long courseId, String courseCode, String courseName, int availableSeats) { }

@Service
public class RegistrationService {
    // constructor gains SemesterService:
    public RegistrationService(StudentRepository, CourseRepository, RegistrationRepository, AuditLogRepository,
                               SemesterService semesterService, PlatformTransactionManager, Clock);

    /** register(): unchanged signature, retry design (MAX_ATTEMPTS = 5) and FR6 post-exhaustion full check.
     *  One attempt, in this order, in one transaction:
     *   1. student else StudentNotFoundException
     *   2. course else CourseNotFoundException (null/0/unknown)
     *   3. NEW: if !semesterService.isRegistrationOpen(course.getSemester().getCode())
     *           -> RegistrationClosedException(semesterCode)
     *   4. duplicate -> AlreadyRegisteredException      (was step 3)
     *   5. availableSeats <= 0 -> CourseFullException     (was step 4)
     *   6. seat -1 + courseRepository.saveAndFlush(course) (@Version), 7. Registration, 8. REGISTER audit  (unchanged)
     *  RegistrationClosedException is not a ConcurrencyFailureException, so it is never retried. */
    public RegistrationResult register(String studentId, Long courseId);

    /** NEW. NOT @Transactional itself. Same retry loop as register (up to 5 attempts, each its own
     *  TransactionTemplate transaction, retried only on ConcurrencyFailureException; after 5 failed attempts throws
     *  RegistrationConflictException. There is no "full" check for drops).
     *  One attempt, in this order, in one transaction:
     *   1. studentRepository.findByStudentId(studentId) else StudentNotFoundException(studentId)
     *   2. courseRepository.findById(courseId) else CourseNotFoundException (also null/0/negative)
     *   3. if !semesterService.isRegistrationOpen(course.getSemester().getCode()) -> DropClosedException(semesterCode)
     *   4. registration = find by (student, course) else NotRegisteredException(courseCode)
     *   5. registrationRepository.delete(registration) and flush (a concurrent delete of the same row surfaces as a
     *      ConcurrencyFailureException, which retries and then ends in NotRegisteredException)
     *   6. course.setAvailableSeats(Math.min(course.getCapacity(), course.getAvailableSeats() + 1));
     *      courseRepository.saveAndFlush(course)   (exactly one saveAndFlush(Course) per attempt; the @Version check)
     *   7. auditLogRepository.save(new AuditLog(studentId, course.getId(), AuditAction.DROP, LocalDateTime.now(clock)))
     *  Any exception rolls the whole attempt back (registration, seats, audit). */
    public DropResult drop(String studentId, Long courseId);
}
```
The common retry loop may be shared between `register` and `drop` by the coder, as long as the observable behaviour above holds (`saveAndFlush(Course)` call counts per attempt, 5 attempts, FR6's final read-only full check only for `register`).

```java
public record RegisteredCourse(Long registrationId, Long courseId, String code, String name, int credits,
        String instructor, String semesterCode,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime registeredAt) { }

public record RegistrationSchedule(SemesterOption semester,              // null if no semester selected
        List<SemesterOption> semesters,                                  // all, code descending
        RegistrationPeriodInfo registrationPeriod,                       // null iff semester is null
        List<RegisteredCourse> courses,                                  // code ascending
        int totalCredits) { }                                            // sum of courses[].credits, computed here

@Service
public class ScheduleService {
    public ScheduleService(StudentRepository, SemesterRepository, RegistrationRepository, SemesterService);

    /** Read-only transaction. studentId unknown -> StudentNotFoundException(studentId).
     *  semesterCode null/blank -> the current semester; if none: (null, semesters, null, List.of(), 0).
     *  Given and unknown -> SemesterNotFoundException. Rows are the student's registrations for courses of the
     *  selected semester, ordered by course code ascending. totalCredits = sum of the listed courses' creditHours.
     *  Never depends on the period: rows are listed whether the period is open or not (FR7-AC3). */
    public RegistrationSchedule getSchedule(String studentId, String semesterCode);
}
```
`CourseService` (constructor gains `SemesterService` as its last parameter) puts the period in its listing:
```java
public record CourseListing(SemesterOption semester, RegistrationPeriodInfo registrationPeriod, List<CourseSummary> courses) { }
// registrationPeriod = semesterService.getRegistrationPeriod(semester.code), or null when semester is null
```

Exceptions (package `edu.tku.registration.service`, all extend `RuntimeException`, `getMessage()` is the user-facing text):
| Class | Constructor | `getMessage()` | HTTP |
|---|---|---|---|
| `RegistrationClosedException` | `(String semesterCode)` | `Registration is closed for semester {semesterCode}.` | 409 |
| `DropClosedException` | `(String semesterCode)` | `Dropping is closed for semester {semesterCode}.` | 409 |
| `NotRegisteredException` | `(String courseCode)` | `You are not registered for {courseCode}.` | 404 |

Existing exceptions are reused: `StudentNotFoundException` (404 `Student not found.`), `CourseNotFoundException` (404 `Course not found.`), `SemesterNotFoundException` (404 `Semester not found.`), `RegistrationConflictException` (409), `CourseFullException` (409), `AlreadyRegisteredException` (409).

### 3.3 Web layer
New DTO (package `edu.tku.registration.web`): `DropResponse(String message, Long courseId, String courseCode, int availableSeats)`. `RegistrationSchedule`, `RegisteredCourse`, `RegistrationPeriodInfo` and the changed `CourseListing` are serialised as they are (JSON names equal record component names).

`RegistrationController` gets (constructor `(RegistrationService, ScheduleService)`; the student id comes **only** from `Principal.getName()`):
```java
@GetMapping("/api/registrations")                 RegistrationSchedule list(@RequestParam(required = false) String semester, Principal p)
@DeleteMapping("/api/registrations/{courseId}")   ResponseEntity<DropResponse> drop(@PathVariable Long courseId, Principal p)
// drop: 200 + new DropResponse("Successfully dropped " + r.courseCode() + ".", r.courseId(), r.courseCode(), r.availableSeats())
```
`ApiExceptionHandler` gains: `NotRegisteredException` → 404 `ex.getMessage()`; `RegistrationClosedException` and `DropClosedException` → 409 `ex.getMessage()`; `org.springframework.web.method.annotation.MethodArgumentTypeMismatchException` → 400 `Malformed request.` (must be mapped explicitly, otherwise the catch-all gives 500).

REST API (ROLE_STUDENT required for all; CSRF header required for DELETE and POST):
| Method | Path | Request | Success | Failures (status + JSON) |
|--------|------|---------|---------|--------------------------|
| GET | /api/registrations | optional query `semester` (code) | 200 `RegistrationSchedule` (example below) | 401 `Authentication required.` · 404 `Semester not found.` · 404 `Student not found.` |
| DELETE | /api/registrations/{courseId} | no body | 200 `{"message":"Successfully dropped CS101.","courseId":1,"courseCode":"CS101","availableSeats":50}` | 400 `Malformed request.` (non-numeric id) · 401 `Authentication required.` · 403 `Invalid or missing CSRF token.` · 404 `Course not found.` · 404 `Student not found.` · 404 `You are not registered for {code}.` · 409 `Dropping is closed for semester {semesterCode}.` · 409 `The course was just updated by another student. Please try again.` |
| POST | /api/registrations | `{"courseId": 1}` | 201 (unchanged) | existing failures plus 409 `Registration is closed for semester {semesterCode}.` (checked before the duplicate and full checks) |
| GET | /api/courses | `semester`, `q` (unchanged) | 200 `{"semester":…,"registrationPeriod":{"startAt","endAt","open"} or null,"courses":[…]}` | unchanged |

`GET /api/registrations` example (Carol, Clock `T_OPEN`):
```json
{
  "semester": { "code": "115-1", "name": "2026 Fall", "current": true },
  "semesters": [ { "code": "115-1", "name": "2026 Fall", "current": true },
                 { "code": "114-2", "name": "2026 Spring", "current": false } ],
  "registrationPeriod": { "startAt": "2026-08-01T00:00:00", "endAt": "2026-12-31T23:59:59", "open": true },
  "courses": [ { "registrationId": 1, "courseId": 3, "code": "MA101", "name": "Calculus I", "credits": 4,
                 "instructor": "Prof. Huang", "semesterCode": "115-1", "registeredAt": "2026-08-10T10:00:00" } ],
  "totalCredits": 4
}
```
Date-times are always `yyyy-MM-dd'T'HH:mm:ss`, Asia/Taipei local, no offset. A drop uses the path id as the **course id** (never a registration id), so a student can only ever drop their own registration (FR4-EX4).

Frontend routes: **no new route and no new nav item.** `/dashboard` gains the registered-courses card; `/courses` gains the banner. The sidebar keeps its two items (`Dashboard`, `Courses`).

### 3.4 User-facing messages (exact text)
Backend (JSON `message`):
| Key | Text |
|-----|------|
| register.closed | Registration is closed for semester {semesterCode}. |
| drop.success | Successfully dropped {courseCode}. |
| drop.closed | Dropping is closed for semester {semesterCode}. |
| drop.notRegistered | You are not registered for {courseCode}. |

(`Course not found.`, `Semester not found.`, `Student not found.`, the conflict text, `Malformed request.`, `Authentication required.`, `Invalid or missing CSRF token.` are unchanged.)

Frontend:
| Key | Text |
|-----|------|
| period.open | Registration is open. Period: {start} to {end}. |
| period.closed | Registration is closed. Period: {start} to {end}. |
| period.none | Registration is closed. No registration period is scheduled for this semester. |
| period.rowNote | Registration is closed. |
| registered.title | My registered courses |
| registered.semester.label | Semester (option text `{name} ({code})`) |
| registered.columns | Code / Name / Credits / Instructor / Registered On / Action |
| registered.total.label | Total credit hours: |
| registered.empty | You have not registered for any courses in this semester. |
| registered.empty.noSemester | No semester has been selected. |
| registered.error | Unable to load your registered courses. Please try again later. |
| drop.button | Drop |
| drop.confirm.text | Drop {code}? |
| drop.confirm.button | Confirm drop |
| drop.cancel.button | Cancel |
| drop.error.generic | Unable to drop the course. Please try again later. |
| drop.closed.note | Dropping courses is only possible during the registration period. |
| print.button | Print |
| csv.button | Download CSV |
| csv.fileName | registered-courses-{semesterCode}.csv |
| csv.header | Course Code,Course Name,Credits,Instructor,Semester,Registered At |
| csv.totalRow | Total Credits,,{total},,, |

`{start}` and `{end}` are `YYYY-MM-DD HH:mm` (API string, `T` replaced by a space, first 16 characters), as on the dashboard. `drop.success` and 4xx drop errors show the server `message` as is.

### 3.5 Frontend structure
| File | Exports / change |
|---|---|
| `src/api/types.ts` (extended) | `RegistrationWindow { startAt: string \| null; endAt: string \| null; open: boolean }`; `CourseListing` gains `registrationPeriod?: RegistrationWindow \| null` (optional on purpose, see FR7-UI6); `RegisteredCourse { registrationId: number; courseId: number; code: string; name: string; credits: number; instructor: string; semesterCode: string; registeredAt: string }`; `RegistrationSchedule { semester: SemesterOption \| null; semesters: SemesterOption[]; registrationPeriod: RegistrationWindow \| null; courses: RegisteredCourse[]; totalCredits: number }`; `DropResult { message: string; courseId: number; courseCode: string; availableSeats: number }` |
| `src/api/registrations.ts` (new) | `fetchRegistrations(semester?: string): Promise<RegistrationSchedule>` (`/api/registrations`, or `/api/registrations?semester=…` via `URLSearchParams`, empty omitted); `dropCourse(courseId: number): Promise<DropResult>` = `apiFetch("/api/registrations/" + courseId, { method: "DELETE" })`. `registerForCourse` stays in `api/courses.ts`. Neither module is imported by the other. |
| `src/utils/scheduleCsv.ts` (new) | `buildScheduleCsv(courses, totalCredits): string`, `scheduleCsvFileName(semesterCode): string` (pure, FR5-UI7) |
| `src/utils/download.ts` (new) | `downloadTextFile(fileName: string, text: string): void`: builds a `Blob([ "﻿", text ], { type: "text/csv;charset=utf-8" })` (the BOM lets Excel read non-ASCII names), creates an object URL, clicks a temporary `<a download=fileName>`, removes it and revokes the URL. Not unit-tested (browser APIs); components call it via this module so tests mock it. |
| `src/components/RegisteredCoursesSection.tsx` (new) | `RegisteredCoursesSection` (no props; uses `useAuth()` for `user` and `markAnonymous`) |
| `src/pages/DashboardPage.tsx` (changed) | renders `<RegisteredCoursesSection/>` as a full-width row below the profile and semester cards, **only when the dashboard state is `loaded`** |
| `src/pages/CoursesPage.tsx` (changed) | period banner, disabled Register and `closed-note-{id}` |
| `src/index.css` (changed) | print stylesheet (below) |

`CoursesPage` changes:
- `open = listing.registrationPeriod ? listing.registrationPeriod.open : true` (absent or null means "no period info", treated as open; the server always sends the object when a semester is selected).
- When `listing.semester` is set and `listing.registrationPeriod` is an object, render `period-banner` above the toolbar (antd `Alert`, `type="success"` when open, `type="warning"` when closed, `showIcon`), with `data-status` `open`/`closed` and the text from §3.4: open or closed with both dates → `period.open`/`period.closed`; both dates null → `period.none`. The banner is rendered also while the table is replaced by `courses-empty`, and it is updated by every listing (semester change, search, refetch after a failed register).
- Register button `disabled = c.registered || pending || full || !open`. When `!open && !c.registered`, render `<span data-testid="closed-note-{id}" class="closed-note">Registration is closed.</span>` under the button (next to the FR6 `full-note`, which keeps its own rule). No other behaviour changes. The server stays the authority: a stale page that sends the request gets 409 and the existing 4xx flow shows the message and refetches.

`RegisteredCoursesSection` behaviour:
- On mount it calls `fetchRegistrations()` once with no argument. `selected` semester state is `null` until the first response, then the response's `semester.code`. Changing the select calls `fetchRegistrations(code)` and clears the feedback. Every response replaces the whole state (rows, total, period, semesters). While a request is pending and there is no data yet: `registered-loading`. The select shows the response's `semesters` (if the response has `semester` but it is not in the list, add it, as `CoursesPage` does). If `semester` is null the select has the placeholder option `""` with text `Select a semester`.
- Failure: 401 `ApiError` → `markAnonymous()`; anything else → `registered-error`, and the rest of the dashboard stays visible. A failing section never affects the profile or semester cards.
- `open = registrationPeriod?.open === true`. When `registrationPeriod` is non-null and `!open`, show `drop-closed-note`.
- Drop flow: clicking `drop-btn-{courseId}` (enabled only when `open`) clears the feedback and sets `confirming = courseId` (one at a time). The action cell then shows `drop-confirm-text-{courseId}`, `drop-confirm-{courseId}` and `drop-cancel-{courseId}`. Cancel clears `confirming`. Confirm: marks `pending` (button `loading`, further clicks ignored), calls `dropCourse(courseId)`, then clears `confirming` and `pending`.
  - Success: `drop-success` shows the server `message`, then the section refetches `fetchRegistrations(selectedCode)` (the total and rows come from the server, the UI never subtracts credits itself). The alert stays during and after the refetch.
  - `ApiError` 401: `markAnonymous()`. Other 4xx: `drop-error` shows the message and the section refetches. Anything else: `drop-error` shows `Unable to drop the course. Please try again later.`, no refetch.
- Print: `print-btn` calls `window.print()`. CSV: `csv-btn` calls `downloadTextFile(scheduleCsvFileName(semester.code), buildScheduleCsv(courses, totalCredits))`. Both are disabled when there are no rows or no semester. Neither depends on `open`.
- Rendering rules: the card has class `print-area`. The print-only header (`print-header`, class `print-only`) is always rendered when data is loaded and has text: `{user.name} ({user.studentId})`, `{semester.name} ({semester.code})` and `Total credit hours: {totalCredits}`, each as its own element (the three strings must appear exactly this way). The toolbar (select, print and CSV buttons), alerts, notes and the `Action` column cells (column `className="no-print"`) carry class `no-print`.

Print stylesheet (in `src/index.css`):
```css
.print-only { display: none; }
@media print {
  body * { visibility: hidden !important; }
  .print-area, .print-area * { visibility: visible !important; }
  .print-area { position: absolute; left: 0; top: 0; width: 100%; box-shadow: none; }
  .print-area .no-print { display: none !important; }
  .print-area .print-only { display: block !important; }
}
```
Visual direction: reuse the navy theme, antd `Card`, `Table` (pagination off), `Alert`, `Button`, `Tag`; the period banner is full-width above the toolbar on `/courses`. Antd components that do not forward `data-testid` get a wrapper element. Buttons and inputs carry the id on the real element.

### 3.6 UI elements tests rely on
`/courses` (new):
| data-testid | Element | Content / notes |
|---|---|---|
| period-banner | antd `Alert` | `data-status="open"` or `"closed"`, text per §3.4. Absent when no `registrationPeriod` object or no semester. |
| closed-note-{id} | `<span>` under the Register button | `Registration is closed.` Rendered only when the period is closed and the course is not registered. |
| register-btn-{id} | `<button>` | now also disabled when the period is closed |

Dashboard (new):
| data-testid | Element | Content / notes |
|---|---|---|
| registered-card | the `Card` (class `print-area`) | title `My registered courses` |
| registered-semester-select | native `<select>` with `<label>` `Semester` | options `{name} ({code})`, value = code |
| registered-loading | loading wrapper (`Spin`) | |
| registered-error | `Alert type="error"` | `Unable to load your registered courses. Please try again later.` |
| registered-empty | element holding the empty text | `You have not registered for any courses in this semester.` or `No semester has been selected.` |
| registered-table | wrapper of the antd `Table` | headers `Code`, `Name`, `Credits`, `Instructor`, `Registered On`, `Action`. Not shown while `registered-empty` is shown. |
| registered-row-{courseId} | `<tr>` (via `onRow`) | in API order |
| registered-code-{id} / registered-name-{id} / registered-credits-{id} / registered-instructor-{id} / registered-date-{id} | `<span>` in the cell | exact value text only; date `YYYY-MM-DD HH:mm` |
| total-credits | `<span>` | exactly the digits of `totalCredits`; its label `Total credit hours:` is outside the span |
| drop-btn-{id} | `<button>` | text `Drop`; disabled when the period is closed (or no period); hidden while that row is confirming |
| drop-confirm-text-{id} / drop-confirm-{id} / drop-cancel-{id} | `<span>` / `<button>` / `<button>` | `Drop {code}?` / `Confirm drop` (loading while pending) / `Cancel` |
| drop-closed-note | `<span>` or `Alert` wrapper | `Dropping courses is only possible during the registration period.` |
| drop-success / drop-error | `Alert` (success / error) | server message / error text; at most one shown |
| print-btn / csv-btn | `<button>` | `Print` / `Download CSV` |
| print-header | element, class `print-only` | contains `{name} ({studentId})`, `{semester name} ({code})`, `Total credit hours: {n}` |

### 3.7 Test harness assumptions
- `render(<MemoryRouter initialEntries={[…]}><App/></MemoryRouter>)` works with no other providers.
- `vi.mock("../api/registrations")` replaces `fetchRegistrations` and `dropCourse`; `vi.mock("../utils/download")` replaces `downloadTextFile`. Components never call `fetch` directly.
- Every existing frontend test that renders `/dashboard` with a loaded dashboard (or navigates to it) must mock `../api/registrations` (§6.2), otherwise the section would call the real `apiFetch`.

## 4. Assumptions & decisions
1. **FR7 scope.** Registration is allowed only if the **course's own semester** has a period that contains "now". It is not the current semester that decides: a `114-2` course follows `114-2`'s period. Bounds are inclusive. Rejected with HTTP **409** and `Registration is closed for semester {code}.` (409 matches the other business-rule failures. 403 is used by security for CSRF and access denied and would be confusing.)
2. **No period configured means closed (fail-safe).** A semester without a `RegistrationPeriod` row cannot be registered for or dropped from. Nothing in the seed is affected (both semesters have periods). The UI shows `Registration is closed. No registration period is scheduled for this semester.`
3. **Inclusive bounds at second granularity.** "Now" is truncated to whole seconds before comparing, so `23:59:59.999` on the end date is open and no instant between `23:59:59` and midnight is lost. The same helper feeds the dashboard's existing `registrationOpen`, so the two cannot disagree. FR1-EX17 still passes (whole seconds).
4. **Check order for register:** student → course → **period** → duplicate → full. The period comes first among the business rules because it needs only the course. A student already registered for a full course gets `closed` when the period is closed and `already registered` when it is open (FR3-AC4b is unchanged inside the period). Unknown student and unknown course keep their 404 before the period check.
5. **Seed registration periods and the real clock.** The production `Clock` is the system clock. With the seed periods, registration is open from 2026-08-01 to 2026-12-31 for `115-1`, and `114-2` is in the past, so anyone running the app after 2026-12-31 sees "closed" everywhere until periods are changed. That is the intended behaviour, not a bug. There is no admin UI in Phase 1 (periods are edited in `data.sql`).
6. **Drop rule (FR4).** "Before the registration deadline" is implemented as: a drop is allowed **only while the course's semester registration period is open** (the same inclusive window and the same `SemesterService` rule as registering). Before the start is practically impossible (no registration can exist), and after the end it is refused. This is the simplest rule that satisfies "before the deadline" and that FR7 requires. Alternative the human may prefer: allow drops until the period's end but also before start (equal in practice).
7. **Drop failures:** not registered → **404** `You are not registered for {code}.`; outside the period (or no period) → **409** `Dropping is closed for semester {code}.`; course unknown → 404 `Course not found.`; non-numeric id → 400 `Malformed request.`; exhausted lock conflicts → 409 (the register conflict text). For a drop of a not-registered course outside the period the period message wins (check order student → course → period → registration).
8. **Drop endpoint:** `DELETE /api/registrations/{courseId}` (by **course id**), 200 with a JSON body (not 204) because the UI shows the server message and the new seat count. A student can only drop their own registration because the lookup is by (student from the Principal, course) and there is no registration id parameter.
9. **Drop transaction:** same design as register (`TransactionTemplate`, 5 attempts on `ConcurrencyFailureException`, each in a fresh transaction), with exactly one `saveAndFlush(Course)` per attempt so the spy tests of FR3-EX10/11 can be mirrored (FR4-EX7/8). Order inside an attempt: delete the registration and flush, then seat +1 with the `@Version` check, then the audit row.
10. **Seats never exceed capacity:** `availableSeats = min(capacity, availableSeats + 1)`. On a consistent database the minimum is never used. If the data is inconsistent (registration exists while seats equal capacity), the drop still succeeds and the seat count stays at capacity rather than violating `ck_course_seats` (FR4-EX11).
11. **Double submit and races.** Two simultaneous drops of the same registration: the registration delete and the course `@Version` conflict make one fail with a lock conflict, which retries and ends in `NotRegisteredException` (404), so seats rise once. A drop racing a register on a last seat is serialised by `@Version` and retries. The invariant `availableSeats + registrations = capacity` holds after any interleaving. Tests assert the invariants and the audit counts, and accept either `NotRegisteredException` or `RegistrationConflictException` for the loser (as FR3-EX14), because lock timeouts are not deterministic.
12. **Audit (NFR4):** every successful drop writes one `AuditLog(studentId, courseId, DROP, now(clock))` in the same transaction. Rejected drops are not audited (same as FR3 assumption 14).
13. **FR5 listing:** `GET /api/registrations` returns the student's registrations for **one semester** (current by default, `?semester=code` otherwise), ordered by course code. "All registered courses" is covered by the semester selector on the dashboard. There is no all-semesters view in Phase 1 because a credit total across semesters is not meaningful. An unknown code → 404 `Semester not found.`; none current and none given → `semester: null`, empty list, total 0.
14. **Total credits** are computed by `ScheduleService.getSchedule` (sum of `creditHours` of the listed rows) and sent as `totalCredits`. The UI only displays the value.
15. **Where the schedule is shown (FR5-AC1):** on the dashboard (a "My registered courses" card). No dedicated "My Courses" page or nav item is added, because FR5-AC1 says the dashboard displays them and a second page would duplicate it. The sidebar keeps two items, so FR1-UI11 (as changed by FR3) is unchanged.
16. **The registered section is rendered only after the dashboard itself loaded** and handles its own errors without affecting the cards. This keeps the FR1 dashboard error and loading criteria (FR1-UI7, UI13) unchanged.
17. **Semester selector data:** the registrations response carries `semesters`, so the dashboard does not call `fetchSemesters` (FR2-UI1 asserts that `fetchSemesters` is called exactly once after clicking `Courses` from the dashboard).
18. **Drop confirmation:** an inline two-step confirm in the row (**Drop** → **Confirm drop** / **Cancel**), not antd `Popconfirm` or `Modal`. It needs no portal, animation or timers, so it is deterministic in jsdom. A drop therefore costs 2 clicks from the dashboard (NFR5: fewer than 5). Registering stays unconfirmed (NFR5-AC2 unchanged).
19. **Disabled-with-reason after the period ends (FR7-AC3):** rows stay visible, `Drop` is disabled, and a visible note `Dropping courses is only possible during the registration period.` is shown. The note appears when the period object is present and closed, including "not scheduled". Print and CSV stay enabled.
20. **Banner on `/courses`:** an antd `Alert` above the toolbar for the selected semester, `Registration is open. Period: …` or `Registration is closed. Period: …` (or the not-scheduled text). It is based on the `registrationPeriod` object the listing now carries (a new top-level key of `GET /api/courses`; `semester` and the course objects are unchanged). The per-row `closed-note` gives the visible reason next to each disabled button.
21. **Compatibility default (frontend):** when a listing has no `registrationPeriod` key the page behaves as before (open, no banner). This keeps the FR2/FR3/FR6 frontend fixtures valid. The server always sends the key.
22. **Refresh after a rejected register** is the existing FR3 flow (4xx → show message → refetch), so a stale "open" banner flips to "closed" after a 409 closed rejection (FR7-UI7).
23. **Print (FR5-AC3):** `window.print()` plus a `@media print` stylesheet that shows only the card (class `print-area`) with a print-only header (name, student ID, semester, total) and no Action column. Tested: the click calls `window.print` and the header text exists. The actual layout is a **manual check** (FR5-MAN1). No PDF library.
24. **Download CSV (FR5-AC3):** client-side, from the data already loaded. File name `registered-courses-{semesterCode}.csv`. Columns `Course Code,Course Name,Credits,Instructor,Semester,Registered At`, `\r\n` line breaks without a trailing newline, a final row `Total Credits,,{total},,,` (the total is the server's value). RFC 4180 quoting (comma, quote, CR/LF → quoted, `"` doubled). CSV-injection guard: text fields starting with `=`, `+`, `-`, `@` or a tab get a leading `'`. A UTF-8 BOM is added by `downloadTextFile` (not part of the string tested). Tested as a pure function, plus a component test with the download helper mocked.
25. **Date format:** `registeredAt` is `YYYY-MM-DD HH:mm` in the table and the CSV, `yyyy-MM-dd'T'HH:mm:ss` in JSON (Taipei local, as the rest of the API).
26. **Seed data is unchanged**, and SK-AC3 counts stay `3 / 2 / 4 / 1 / 2 / 0`. Tests that need other periods, semesters, courses or registrations create them (`NOPD-1`, `PAST-1`, `FUT-1`, `LAST1`-style courses) and either roll back (`@Transactional`) or remove them afterwards (as `CapacityTestData.removeCourse`, plus deleting the test semesters and periods). Dropping Carol's seed registration is only done in rolled-back tests or restored.
27. **Clock for all tests that register or drop.** Using the real clock would make those tests fail from 2027-01-01 (the period ends 2026-12-31). Every such test pins a `Clock` as described under §2 (see §6.1).
28. **NFR2:** every new endpoint (`GET /api/registrations`, `DELETE /api/registrations/{id}`) is under `/api/**` and so needs `ROLE_STUDENT` (401 JSON when anonymous). `DELETE` needs the CSRF token (403 JSON when missing). The student id comes from the security context only. No security configuration change is needed.
29. **NFR5 click counts:** register is 2 clicks from the dashboard (Courses → Register), unchanged; drop is 2 clicks (Drop → Confirm drop), 3 counting Log in. The banner and notes add no clicks.
30. **Size and split.** About 50 backend criteria and 25 frontend criteria, in two tidy slices that touch different layers. Recommended: **one `/build-feature` run, two sequential passes against this one spec**: pass A = Slice A (backend: §3.1–§3.4, all *backend*/service/mvc criteria, with `-Dskip.npm -Dskip.installnodenpm` for fast runs), pass B = Slice B (frontend: §3.5–§3.7, all *frontend*/*unit* criteria). Each pass ends with a green `mvn -q test`. If a single pass proves too big, the natural third cut is inside B (B1: `/courses` banner and disabled Register, FR7-UI1…UI8; B2: the dashboard section, the rest), because B1 only needs `registrationPeriod` from §3.3. No separate spec is needed.
31. **Unchanged:** the `Course` check constraint, `@Version`, BCrypt, the CSRF cookie flow and the 401/403 JSON handlers. `CurrentSemesterInfo` (dashboard) keeps its shape.

## 5. Out of scope
- Admin screens or APIs to edit registration periods, semesters or courses (periods are only seeded).
- Per-student or per-course deadlines, add/drop windows that differ from the registration period, late-drop penalties, a "W" grade or refunds.
- A schedule across all semesters, a weekly timetable view or time-conflict checks, a dedicated "My Courses" page or nav item.
- PDF export, server-side generated files, email delivery, a printable page other than the browser's print of the card.
- Waitlists and automatic enrolment when a seat is released by a drop.
- Auditing rejected attempts (unchanged from FR3), a machine-readable error `code` field, and a drop-by-registration-id endpoint.
- Real-time updates (no polling) and browser end-to-end tests.

## 6. Changes to existing criteria and tests
Everything not listed keeps passing unchanged. The tester updates tests, and the coder never edits `src/test/`.

### 6.1 Backend
| Existing item | Change |
|---|---|
| FR3 spec §3.2 ("The registration period is **not** checked", "The semester is not checked") and assumption 10 | Superseded. Registration is now period-checked (FR7). |
| **FR3-EX5** (`RegistrationControllerTest`, "a course of another semester (CS102 in 114-2) can be registered"; Clock `2026-09-01`) | **Must change.** At `T_OPEN` the POST now returns 409 `Registration is closed for semester 114-2.` The "can be registered" case is re-expressed with the clock at `T_SPRING` (201, `Successfully registered for CS102.`, seats 44), as FR7-AC1c. The `@DisplayName` still starts with `FR3-EX5`. |
| **FR3-EX6** ("Until FR7 the period is not enforced ... Clock `2027-06-01` still returns 201") | **Must be reversed / replaced.** At `T_LATE` the POST now returns 409 `Registration is closed for semester 115-1.` (FR7-AC1b). The `@DisplayName` starts with `FR3-EX6` or the test is removed and covered by FR7-AC1b. |
| FR3-AC1…AC6, EX1–EX4, EX7–EX9 (`RegistrationControllerTest`, Clock fixed at `2026-09-01T09:00`) | Unchanged: `T_OPEN` is inside `115-1`. |
| Tests that call `register` without pinning the Clock: `CourseFullServiceTest`, `CourseFullControllerTest`, `CourseFullConcurrencyTest`, `CourseFullRetryTest`, `RegistrationConcurrencyTest`, `RegistrationRetryAndAtomicityTest` (FR3-EX8, EX10–EX14, FR6-AC1…AC3d, FR6-EX1/EX2/EX5) | **Must pin the Clock** to `T_OPEN` (for example a `@Primary` fixed test `Clock` in a `@TestConfiguration`, or `@MockitoBean`), because their courses live in `115-1` and would fail after the period ends (a time bomb from 2027-01-01). Expected values and assertions do not change. |
| FR3-EX9 and NFR4-AC1 | Unchanged. NFR4-AC2 (drop audit) is added. |
| FR2 `CourseControllerTest` / `CourseServiceTest` | FR2-AC1a, AC2, EX9 still hold (`semester` and course objects unchanged). Any assertion on the **exact top-level key set** of `GET /api/courses` must now allow `registrationPeriod`. FR2-EX5 additionally gets `registrationPeriod == null` (optional). |
| `SemesterServiceTest` (FR1-EX17, EX18) | Unchanged (constructor and whole-second behaviour unchanged). FR7-EX4 adds the truncation and `isRegistrationOpen` cases. |
| `DashboardControllerTest` (FR1-AC3) | Unchanged. FR7-EX7 adds the end-second cases. |
| NFR2-AC2 (`SecurityAccessTest`, anonymous `GET /api/registrations` → 401) | Unchanged and still passes. The FR3 note "404 for authenticated GETs until FR5" no longer holds, and no test asserted it. |
| SK-AC3 (`SkeletonContextTest`) | Unchanged (`3 / 2 / 4 / 1 / 2 / 0`). Every new test restores the seed or rolls back. |
| FR6-EX4 (invariant on seed data) | Unchanged. It is extended by FR4-AC3 and FR4-EX10 for drops. |
| `RegistrationConstraintTest`, `CourseSeatsConstraintTest`, `CourseOptimisticLockingTest` | Unchanged (repository level, no period). |

New backend test classes the tester is expected to add (names are the tester's choice): registration-period enforcement, drop controller (rolled back), drop service (retry, atomicity, concurrency; not `@Transactional`, `@DirtiesContext(AFTER_CLASS)`), schedule controller and service, semester period service.

### 6.2 Frontend
| Existing item | Change |
|---|---|
| FR1-UI11 (sidebar `Dashboard`, `Courses`) | Unchanged (no new nav item). |
| `DashboardPage.test.tsx` (FR1-UI8…UI13), `LoginPage.test.tsx` (FR1-UI2 shows the dashboard after login), `routing.test.tsx` (FR1-UI5, UI6) | Add `vi.mock("../api/registrations", …)` resolving an empty or fixture schedule, so the new section makes no real network call. No expectation changes. FR1-UI13(b) (dashboard error) shows no section. |
| `CoursesPage.test.tsx` and `CourseFullUi.test.tsx` (start at `/dashboard`, FR2-UI1, NFR5-AC1) | Add the same `../api/registrations` mock. `fetchSemesters` is still called exactly once (the section does not call it). NFR5-AC1 still counts 2 clicks. |
| FR2/FR3/FR6 fixtures (`coursesFixtures.tsx`) | Unchanged work: listings without `registrationPeriod` behave as open (FR7-UI6). The tester may add `registrationPeriod: OPEN_PERIOD` to new fixtures. FR3-UI1, UI3, UI4…, FR6-UI* keep passing. |
| FR3-UI7 and FR6 | Already replaced by FR6 (unchanged by this spec). |
| `coursesApi.test.ts` (FR2-UI8, FR3-UI8) | Unchanged (`api/courses.ts` unchanged). FR5-UI9 covers the new module. |
