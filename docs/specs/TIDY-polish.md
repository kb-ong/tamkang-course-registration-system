# Spec: TIDY-polish — non-blocking review follow-ups

Requirements covered: none new. Applies the approved non-blocking suggestions from `.pipeline/FR2-FR3-courses-register/review.md`, `.pipeline/FR6-capacity/review.md` and `.pipeline/FR4-FR5-FR7-drop-schedule-period/review.md`. FR1 to FR7 behaviour, API contracts and user-facing strings do not change.
Status: Draft (approved at the /build-feature human gate)

## 1. Summary
A small cleanup run: a README and a seed-data warning, a refactor and a log line in RegistrationService, a stricter CSV formula guard, a safer download revoke, keyboard focus and screen-reader links on the dashboard and /courses, and one test-only diagnostic. No new endpoints, no new messages.

## 2. Acceptance criteria
| ID | Criterion | Test level |
|----|-----------|------------|
| TIDY-1 | README.md and a data.sql comment exist as in 3.1. | none/manual (docs only; reviewer reads them) |
| TIDY-2 | Refactor in 3.2 with no behaviour change. | none (existing backend tests stay green; no new test) |
| TIDY-3 | Retry exhaustion logs one `warn` (3.3). | backend (optional log-appender test) |
| TIDY-4 | CSV guard on every string cell (3.4). | frontend unit |
| TIDY-5 | Object URL revoked in `setTimeout(..., 0)` (3.5). | frontend unit |
| TIDY-6a | Drop click moves focus to that row's Confirm button. | frontend |
| TIDY-6b | Cancel returns focus to that row's Drop button. | frontend |
| TIDY-6c | After a drop finishes (success or error), focus is on the feedback wrapper. | frontend |
| TIDY-6d | Disabled Drop buttons have `aria-describedby="drop-closed-note"`; the note has `id="drop-closed-note"`. | frontend |
| TIDY-6e | Disabled Register buttons on /courses carry `aria-describedby` listing the note ids that exist. | frontend |
| TIDY-6f | `.full-note` font-size is 13px. | frontend (CSS file text check) |
| TIDY-7a | RegistrationConcurrencyTest prints a conflict count (optional). | backend (test-only) |
| TIDY-7b | Both FR6-EX2 readings are documented as covered. | none (documentation) |

## 3. Contract

### 3.1 TIDY-1 Seed warning and README
- `src/main/resources/data.sql`: directly above `INSERT INTO registration_period` (currently line 24), add these comment lines exactly:
  ```
  -- WARNING: the 115-1 period below ends 2026-12-31 23:59:59. After that date the app reports
  -- registration as closed (register and drop are refused). To keep working, edit the 115-1 end_at on the
  -- first VALUES row (currently line 25, the TIMESTAMP '2026-12-31 23:59:59' value). Do not change it in this task.
  ```
  After inserting 3 lines the row moves to line 28; the README must say "the `115-1` row of the `INSERT INTO registration_period` statement at the end of `src/main/resources/data.sql`" and name the value `2026-12-31 23:59:59`, not a fixed line number.
- `README.md` at the repo root, sections in this order:
  1. Title `Tamkang Course Registration System` and 2 to 3 sentences: Phase 1 student course registration web app (Spring Boot backend, React frontend, H2 in-memory DB).
  2. `## Important: registration period` with a bold note: the seeded `115-1` registration period ends on **2026-12-31 23:59:59**; after that date the app reports registration as closed. Edit the `115-1` row in the `registration_period` insert at the end of `src/main/resources/data.sql`.
  3. `## Run`: `mvn spring-boot:run`, then http://localhost:8080. The implementer must check `frontend/package.json`, the pom and `application.properties` and describe only what is true (for example, whether the built frontend is served by Spring or runs with `npm run dev`). Nothing unverified.
  4. `## Seed logins`: `410000001` (Alice Chen), `410000002` (Bob Lin), `410000003` (Carol Wu), all with password `password123` (FR1 spec seed section).
  5. `## Tests`: `mvn -q test` for the backend; frontend: the actual npm script from `frontend/package.json` (check it), run from `frontend/`.

### 3.2 TIDY-2 Refactor (src/main only)
- Delete `import edu.tku.registration.domain.Semester;` from `SemesterService` only if it is unused (the reviewer says it is; confirm by compile).
- `RegistrationService`: add `import java.util.Optional;`. Add
  ```java
  private Course loadCourse(Long courseId) {
      Optional<Course> found = courseId == null ? Optional.empty() : courseRepository.findById(courseId);
      return found.orElseThrow(() -> new CourseNotFoundException(courseId));
  }
  ```
  and use it in `attemptRegister` and `attemptDrop`. Order of checks, exceptions and transactions unchanged.

### 3.3 TIDY-3 Retry-exhaustion warning
- `RegistrationService` gets `private static final org.slf4j.Logger log = LoggerFactory.getLogger(RegistrationService.class);`.
- In `register`, after the loop and before the final read-only course-full check, and in `drop` after the loop: if the last caught `ConcurrencyFailureException` is `last`, call
  `log.warn("Registration {} for course {} failed after {} attempts due to concurrent updates: {}: {}", "register" | "drop", courseId, MAX_ATTEMPTS, last.getClass().getName(), last.getMessage());`
  Keep a `ConcurrencyFailureException last` variable assigned in the catch block. Exact argument order: operation, courseId, MAX_ATTEMPTS, exception class name, exception message.
- The message must not contain the student id, name or email. Thrown exceptions and their messages are unchanged (including the Course Full branch of register, which still happens after the log).
- Test (optional, backend): a Mockito or spy-based `RegistrationService` test where `courseRepository.saveAndFlush` throws `ObjectOptimisticLockingFailureException` five times, with a Logback `ListAppender` on `RegistrationService`: exactly one WARN event whose formatted message contains the course id and `ObjectOptimisticLockingFailureException` and does not contain the student id used. `@DisplayName` starts with `TIDY-3`. Tester may skip it; if skipped, say so in the report.

### 3.4 TIDY-4 CSV guard (`frontend/src/utils/scheduleCsv.ts`)
- Trigger test becomes `/^[=+\-@\t\r]/`. Guarded value is `'` + value, then RFC 4180 `quote` as before (so a leading `\r` value becomes `"'\rX"`).
- Guarded cells (via `text()`): code, name, instructor, **semesterCode**.
- Unguarded: credits (`String(c.credits)`, even negative), Registered At (`quote(formatDateTime(...))`), the header row, and the `Total Credits,,N,,,` row.
- FR5-UI7 (c) in the existing spec stays valid. Add to it: semesterCode `=X` gives a cell `'=X`; a leading `\r` in name gives `"'\rA"`; Registered At is never prefixed. Add the new cases in new `it(...)` blocks with `@DisplayName`-style titles starting `TIDY-4`; do not edit existing FR5-UI7 tests.

### 3.5 TIDY-5 Download revoke (`frontend/src/utils/download.ts`)
- Order: append link, `click()`, `removeChild(link)` (synchronous), then `setTimeout(() => URL.revokeObjectURL(url), 0)`.
- Test with `vi.useFakeTimers()`: stub `URL.createObjectURL` (returns `blob:x`) and `URL.revokeObjectURL`; after `downloadTextFile("a.csv","x")`: the link is no longer in `document.body`, `revokeObjectURL` has not been called; after `vi.runAllTimers()` (or `advanceTimersByTime(0)`) it was called once with `blob:x`.

### 3.6 TIDY-6 Focus and accessibility

Dashboard (`RegisteredCoursesSection.tsx`):
- TIDY-6a: after the Drop click, the row's Confirm button (`data-testid="drop-confirm-{courseId}"`) has `document.activeElement`.
- TIDY-6b: clicking Cancel (`drop-cancel-{courseId}`) makes `drop-btn-{courseId}` the active element.
- TIDY-6c: wrap the feedback Alert in `<div id="drop-feedback" data-testid="drop-feedback" tabIndex={-1}>`; the Alert keeps its own `data-testid` (`drop-success` / `drop-error`). When a drop finishes (success or error, but not 401), focus moves to `#drop-feedback`. Reason: the Drop and Confirm buttons unmount (and the row may disappear after the refetch), so focus would fall to body; the feedback is a stable element whose content (an `role="alert"`) tells the user the result. Do not move focus when Cancel is used or on a semester change. The wrapper renders only when `feedback` is set. Tests: after a successful drop and after a 409 drop, `document.activeElement` is the element with `id="drop-feedback"`.
- TIDY-6d: the closed Alert gets `id="drop-closed-note"` (keeps `data-testid="drop-closed-note"`). Each Drop button that is disabled (`!open`) has `aria-describedby="drop-closed-note"`. When open the attribute is absent. Test: closed fixture, every `drop-btn-*` has the attribute and `document.getElementById("drop-closed-note")` exists with the text `Dropping courses is only possible during the registration period.`; open fixture, no attribute.

/courses (`CoursesPage.tsx`):
- TIDY-6e: the full note span gets `id="full-note-{id}"` and the closed note span `id="closed-note-{id}"` (testids unchanged). The Register button gets `aria-describedby` equal to the space-separated ids of the notes actually rendered, in the order `full-note-{id}` then `closed-note-{id}`. Examples: full and open gives `full-note-5`; not full and closed gives `closed-note-5`; full and closed gives `full-note-5 closed-note-5`. When neither note renders (available, open, or already registered) the attribute is absent (not an empty string). Condition for each id equals the existing render condition of the note (`!c.registered && availableSeats <= 0`; `!c.registered && !open`).
- TIDY-6f: `frontend/src/index.css` `.full-note` becomes `font-size: 13px;`. `.closed-note` stays 12px. Test reads the CSS file as text and asserts the `.full-note` block contains `font-size: 13px`.

### 3.7 TIDY-7 Test-only (src/test)
- TIDY-7a (optional): in `RegistrationConcurrencyTest`, count how many attempts hit a `ConcurrencyFailureException`/conflict across rounds (for example, a spy on the repository or an `AtomicInteger` in a wrapper) and print one line `TIDY-7a: saveAndFlush conflicts observed = N`. No assertion on N. Skip if it needs more than a few lines or changes assertions.
- TIDY-7b: the tester's report states that both FR6-EX2 readings are covered by the existing tests. Documentation only.

### 3.8 Web layer, messages, data model
No change to REST endpoints, JSON, status codes, user-facing strings, or the data model. Seed data values do not change (only comments).

## 4. Assumptions & decisions
1. data.sql gets a comment only; period dates are not changed.
2. README line references use the table and value, not a line number, since the comment shifts line numbers. The comment itself names the row.
3. README content about running the frontend is verified against `package.json` by the coder instead of fixed here.
4. The TIDY-3 log is emitted for the Course Full branch too, because retries were exhausted either way. Log level warn, logger `RegistrationService`. A logged exception message from Spring could in theory contain entity info (course id and version), but never student data; accepted.
5. Log test is optional because it needs Logback internals; the item is otherwise untested.
6. Guarding the CSV semester code does not change real output (codes like `115-1` do not start with a trigger). A value like `-1` would now be prefixed, accepted. Date and credits stay unguarded as instructed.
7. Focus after a drop goes to the feedback wrapper (both success and error), not the card heading, because it reports the result and does not move when the table reloads. Alert already has `role="alert"`, so this may be announced twice by some readers; accepted.
8. For TIDY-6a/6b, focus is moved in an effect after the state change, so tests should use `await waitFor(...)` or `findBy`.
9. `aria-describedby` on a disabled antd Button is set directly on the `<button>`; disabled buttons are not focusable, but screen readers in browse mode still read the description.
10. The closed note on the dashboard exists only when the period data is loaded; Drop buttons only exist after load, so the reference is never dangling.
11. Dropped from the review lists as out of scope (see section 5): suggestions about dead `request == null` branch, the success message constant, search-reload staleness, Vitest timeout, and `retryAfterOneConflict`. Item 8 of the drop review (date-forced run) is not done.
12. TIDY-1: the data.sql comment refers to the 115-1 row by name and value, not by line number.

## 5. Out of scope
- Semester-select behaviour on a failed load (cosmetic, drop review item 6).
- Catching `DataIntegrityViolationException` from the course save.
- Any new feature or requirement; any change to API contracts or user-facing strings.
- Changing the seeded period dates.
- Other unlisted review suggestions: dead `request == null` branch in RegistrationController, register.success constant, CoursesPage search-reload staleness, Vitest `testTimeout`, date-forced test run for 2026-12-31.
