# Spec: FR2-FR3-courses-register — View/Search Courses and Register for a Course

Requirements covered: FR2 (AC1–AC3), FR3 (AC1–AC3), NFR1 (course search under 3 s), NFR4 (audit of registrations), NFR5 (fewer than 5 clicks). It also keeps NFR2 true for the new endpoints.
Status: Draft (approved at the /build-feature human gate)

## 1. Summary
A signed-in student opens a new **Courses** page from the sidebar. It lists the courses of the selected semester (the current semester by default, with a semester selector). The student can search by course code or name. Each row shows code, name, credits, instructor and available seats. The student clicks **Register** on a row, with no confirmation dialog, and sees `Successfully registered for CS101.`. The course is added to the student's registrations, seats drop by 1, an audit row is written, and the row shows a **Registered** tag with a disabled button. Duplicate registration and registering for a course with no seats left are rejected with clear messages. Two students racing for the last seat can never both succeed and seats never go below 0.

**Build slices (same approach as FR1).** Slice A, backend: §3.1–§3.4 and all criteria marked *backend*. Slice B, frontend: §3.5–§3.7 and all criteria marked *frontend*. Slice B depends on the §3.3 API contract only, and its tests mock the API modules.

## 2. Acceptance criteria

Test levels:
- **backend**: JUnit 5 under `src/test/java`. For HTTP, use `@SpringBootTest` + `@AutoConfigureMockMvc` against the real seed data, with `spring-security-test` (`csrf()`, `user("410000001").roles("STUDENT")`). For services, call the beans directly. "JSON error `X`" means the body is exactly `{"message":"X"}` with a JSON content type (as in FR1).
- **frontend**: Vitest + React Testing Library under `frontend/src/__tests__/`. API modules are mocked with `vi.mock("../api/courses")` and `vi.mock("../api/dashboard")`. The `it` name starts with the criterion ID.

Fixed seed facts used below (unchanged seed): semester `115-1` "2026 Fall" (current) has CS101 (3 credits, Prof. Lee, capacity 50, 50 seats), CS201 "Data Structures" (3, Prof. Chang, 40, 40) and MA101 "Calculus I" (4, Prof. Huang, capacity 1, 0 seats). Semester `114-2` "2026 Spring" has CS102 "Object-Oriented Programming" (3, Prof. Lee, 45, 45). Carol (`410000003`) is registered for MA101. Alice is `410000001`, Bob is `410000002`.

### FR2: View Available Courses (backend)
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR2-AC1a | Given Alice is authenticated, when `GET /api/courses` (no params), then 200 and body `semester` is `{"code":"115-1","name":"2026 Fall","current":true}`, and `courses` has exactly the codes `CS101`, `CS201`, `MA101`, in that order. | backend |
| FR2-AC1b | Search by code, case-insensitive and substring. `q=CS101` returns only CS101. `q=cs` returns CS101 and CS201 in that order. `q=%20ma10%20` (leading and trailing spaces) returns only MA101. | backend |
| FR2-AC1c | Search by name, case-insensitive and substring. `q=programming` returns only CS101 (CS102 is in another semester). `q=DATA` returns only CS201. `q=calc` returns only MA101. | backend |
| FR2-AC2 | Given `GET /api/courses`, then each course object has exactly the keys `id`, `code`, `name`, `credits`, `instructor`, `capacity`, `availableSeats`, `registered`. For CS101 the values are `code` "CS101", `name` "Introduction to Programming", `credits` 3, `instructor` "Prof. Lee", `capacity` 50, `availableSeats` 50, `registered` false. For MA101: `credits` 4, `instructor` "Prof. Huang", `capacity` 1, `availableSeats` 0. | backend |
| FR2-AC3a | When `GET /api/courses?semester=114-2`, then `semester.code` is `114-2` and `courses` is exactly `[CS102]` (credits 3, seats 45). The default listing and `?semester=115-1` never contain CS102. | backend |
| FR2-AC3b | When `GET /api/courses?semester=114-2&q=cs`, then only CS102 is returned. When `GET /api/courses?semester=115-1&q=object`, then `courses` is `[]` (the match is in the other semester). | backend |
| FR2-EX1 | When `q` is absent, empty or only spaces, then the result is the full list for the semester (same as no `q`). | backend |
| FR2-EX2 | When `q=zzz`, then status is 200 and `courses` is `[]`, with `semester` still set. | backend |
| FR2-EX3 | `%` and `_` in `q` are literal characters, not SQL wildcards. `q=%` and `q=_` each return `courses: []`. | backend |
| FR2-EX4 | When `GET /api/courses?semester=999-9`, then 404 with JSON error `Semester not found.` | backend |
| FR2-EX5 | In a `@Transactional` service test, with `115-1` set to `current=false` and flushed: `CourseService.searchCourses("410000001", null, null)` returns `semester == null` and an empty `courses` list. `searchCourses("410000001", "115-1", null)` still returns the three courses. | backend |
| FR2-EX6 | When `GET /api/semesters`, then 200 and the body is exactly `[{"code":"115-1","name":"2026 Fall","current":true},{"code":"114-2","name":"2026 Spring","current":false}]` (ordered by code, descending). | backend |
| FR2-EX7 | `registered` is per student. For Carol, MA101 has `registered` true and CS101, CS201 have false. For Alice, all are false. For Carol with `semester=114-2`, CS102 is false. | backend |
| FR2-EX8 | When an anonymous user sends `GET /api/semesters` and `GET /api/courses?q=cs`, then each response is 401 with JSON error `Authentication required.` | backend |
| FR2-EX9 | The `GET /api/courses` response never contains the keys `version` or `semester` inside a course object (no entity leakage). Courses are sorted by `code` ascending. | backend |
| NFR1-AC1 | Given 1,000 extra courses saved in semester `114-2` (codes `PF0001`…`PF1000`, names containing `Perf`), when `GET /api/courses?semester=114-2&q=perf` is sent as Alice, then the status is 200, 1,000 courses are returned, and the elapsed wall-clock time measured around the MockMvc call is under 3000 ms. The test removes the extra courses afterwards (or uses `@DirtiesContext`). | backend |

### FR3: Register for Course (backend)
Setup for every FR3 MockMvc test unless stated: authenticated as `user("410000001").roles("STUDENT")`, a valid CSRF token, `Content-Type: application/json`, and the `Clock` overridden to fixed `2026-09-01T09:00:00+08:00` (Asia/Taipei). `cs101Id` is the id of CS101 in `115-1`, read through `CourseRepository`.

| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR3-AC1 | When `POST /api/registrations` with `{"courseId": <cs101Id>}`, then status is 201 and the body has exactly the keys `message`, `registrationId`, `courseId`, `courseCode`, `availableSeats`, with `message` `Successfully registered for CS101.`, `courseId` = `cs101Id`, `courseCode` `CS101`, `availableSeats` 49, and `registrationId` a number. | backend |
| FR3-AC2 | After FR3-AC1, there is exactly one `Registration` for (Alice, CS101) whose `registrationDate` is `2026-09-01T09:00:00` (the fixed clock). A following `GET /api/courses` as Alice shows CS101 with `registered` true. The schedule page is FR5. | backend |
| FR3-AC3 | After FR3-AC1, CS101 in the database has `availableSeats` 49 (was 50) and its `version` is greater than before. CS201 and MA101 are unchanged. When Bob then registers for CS101, `availableSeats` is 48. | backend |
| FR3-AC4a | Given Alice has registered for CS101, when she posts the same `courseId` again, then 409 with JSON error `You are already registered for CS101.` Afterwards `availableSeats` is still 49, there is one registration for the pair, and there is one `AuditLog` row. | backend |
| FR3-AC4b | Given Carol (`410000003`), when she posts MA101's id, then 409 `You are already registered for MA101.` (the duplicate check comes before the seats check). The seats stay 0. | backend |
| FR3-AC5 | After FR3-AC1 there is exactly one new `AuditLog` row with `studentId` `410000001`, `courseId` = `cs101Id`, `action` `REGISTER` and `timestamp` `2026-09-01T09:00:00`. (NFR4) | backend |
| FR3-AC6 | Student identity comes only from the security context. Authenticated as Bob, when the body is `{"courseId": <cs101Id>, "studentId": "410000001"}`, then the registration, the audit row and the response all belong to Bob (`410000002`). Alice has no registration for CS101. | backend |
| FR3-EX1 | Seats guard. Authenticated as Bob, when he posts MA101's id (0 seats), then 409 with JSON error `Course MA101 is full.` MA101 `availableSeats` stays 0 (never negative). No `Registration` and no `AuditLog` row is created. FR6 will refine this. | backend |
| FR3-EX2 | When the body is `{"courseId": 999999}` or `{"courseId": 0}`, then 404 with JSON error `Course not found.` Nothing is written. | backend |
| FR3-EX3 | When the body is `{}` or `{"courseId": null}`, then 400 with JSON error `Course ID is required.` When the body is `{"courseId":"abc"}` or `{not json`, then 400 with JSON error `Malformed request.` Nothing is written. | backend |
| FR3-EX4 | When the CSRF token is missing, then 403 with JSON error `Invalid or missing CSRF token.` Nothing is written. | backend |
| FR3-EX5 | Registration is allowed for a course of any semester. When Alice registers for CS102 (`114-2`), then 201 with message `Successfully registered for CS102.` and seats 44. | backend |
| FR3-EX6 | Until FR7, the registration period is not enforced. With the Clock fixed at `2027-06-01T09:00:00+08:00` (outside both seed periods), registering for CS101 still returns 201. | backend |
| FR3-EX7 | Authenticated as `user("499999999").roles("STUDENT")` (no such student), when posting CS101's id, then 404 with JSON error `Student not found.` Nothing is written and seats are unchanged. | backend |
| FR3-EX8 | Atomicity. With `AuditLogRepository` replaced by `@MockitoSpyBean` whose `save` throws `RuntimeException`, when `RegistrationService.register("410000001", cs101Id)` is called, then the exception propagates (the MVC mapping gives 500 `An unexpected error occurred.`), CS101 `availableSeats` is still 50, and there is no `Registration` row. The test is not `@Transactional`. | backend |
| FR3-EX9 | Failed attempts never write audit rows. After FR3-EX1, FR3-AC4 (second attempt), FR3-EX2 and FR3-EX7, the `audit_log` row count equals the number of successful registrations only. (NFR4) | backend |
| FR3-EX10 | Optimistic-lock retry. With `CourseRepository` as `@MockitoSpyBean` configured so that the first `saveAndFlush(any(Course))` throws `ObjectOptimisticLockingFailureException` and later calls run the real method (`doThrow(...).doCallRealMethod()`), when `register("410000001", cs101Id)` is called, then it succeeds. `availableSeats` is 49 (decremented exactly once), there is exactly one registration and exactly one audit row, and `saveAndFlush` was called twice. The test is not `@Transactional`. | backend |
| FR3-EX11 | Exhausted retries. With the same spy configured so that every `saveAndFlush(any(Course))` throws `ObjectOptimisticLockingFailureException`, when `register(...)` is called, then it throws `RegistrationConflictException` with message `The course was just updated by another student. Please try again.` after exactly 5 `saveAndFlush` calls. Seats are unchanged (50), and there is no registration and no audit row. Via HTTP the same case returns 409 with that JSON error. The test is not `@Transactional`. | backend |
| FR3-EX12 | Concurrency, last seat. Given a course `LAST1` in `115-1` created by the test with capacity 1 and 1 available seat, when Alice and Bob call `RegistrationService.register(studentId, lastId)` simultaneously from two threads (released together by a `CountDownLatch`/`CyclicBarrier`), then exactly one call returns and the other throws `CourseFullException` or `RegistrationConflictException`. Afterwards `availableSeats` is 0, there is exactly 1 `Registration` and exactly 1 `REGISTER` audit row for `lastId`. Repeat the race for 20 rounds (re-creating the course each round). The invariants hold in every round. | backend |
| FR3-EX13 | Concurrency, no overselling. Given a course with 2 seats, when Alice, Bob and Carol register simultaneously from three threads, then exactly 2 calls return and 1 throws `CourseFullException` or `RegistrationConflictException`. `availableSeats` is 0 (never negative), and there are exactly 2 registrations and 2 audit rows for the course. | backend |
| FR3-EX14 | Concurrency, same student double submit. Given a course with 50 seats, when Alice calls `register` for it simultaneously from two threads, then exactly one returns and the other throws `AlreadyRegisteredException` or `RegistrationConflictException`. `availableSeats` is 49, with exactly one registration and one audit row. | backend |
| NFR4-AC1 | Every successful registration writes exactly one `AuditLog` row (see FR3-AC5). The row is written in the same transaction as the registration and the seat change (see FR3-EX8). | backend |
| NFR2-AC7b | When an anonymous user sends `POST /api/registrations` with `{"courseId":1}` and a valid CSRF token, then 401 and the `registration` and `audit_log` row counts are unchanged. This is the existing NFR2-AC3, which must keep passing. | backend |

### FR2 / FR3: Frontend (Vitest)
Common setup unless stated: `fetchMe()` resolves Alice's profile. `fetchDashboard()` resolves the FR1 example. `fetchSemesters()` resolves `[{code:"115-1",name:"2026 Fall",current:true},{code:"114-2",name:"2026 Spring",current:false}]`. `fetchCourses` resolves the **listing fixture**: semester `115-1`, courses `CS101` (id 1, 3 credits, Prof. Lee, 50/50 seats, `registered` false), `CS201` (id 2, 3, Prof. Chang, 40/40, false), `MA101` (id 3, 4, Prof. Huang, capacity 1, 0 seats, `registered` true). The app is rendered at `/courses` with `MemoryRouter`.

| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR2-UI1 | Given an authenticated user on `/dashboard`, then `[data-testid="side-nav"]` has exactly two `menuitem`s, in order `Dashboard` and `Courses`. When `Courses` is clicked, then `[data-testid="courses-title"]` has text `Available Courses`, `fetchSemesters` was called once, and `fetchCourses` was called once with exactly `{}` (no keys). On `/courses` the `Courses` item is the selected one. | frontend |
| FR2-UI2 | Given the listing fixture, then `[data-testid="courses-table"]` shows the column headers `Code`, `Name`, `Credits`, `Instructor`, `Available Seats`, `Action`, and three rows `course-row-1`, `course-row-2`, `course-row-3` in that order. For id 1, the exact text content of `course-code-1` is `CS101`, `course-name-1` is `Introduction to Programming`, `course-credits-1` is `3`, `course-instructor-1` is `Prof. Lee`, and `course-seats-1` is `50`. For id 3, `course-seats-3` is `0`. | frontend |
| FR2-UI3 | Given the listing fixture, then `[data-testid="semester-select"]` is a native `<select>` labelled `Semester` (`getByLabelText("Semester")`) with options `2026 Fall (115-1)` (value `115-1`) and `2026 Spring (114-2)` (value `114-2`) in that order, and its current value is `115-1`. When the user selects `114-2` and `fetchCourses` then resolves a listing with semester `114-2` and only CS102 (id 4), then `fetchCourses` was called with exactly `{ semester: "114-2" }`, and the table shows `course-row-4` and no longer shows `course-row-1`. | frontend |
| FR2-UI4 | When the user types `cs` into `[data-testid="course-search-input"]` and clicks `[data-testid="course-search-button"]` (text `Search`), then `fetchCourses` was called with exactly `{ semester: "115-1", q: "cs" }`. Pressing Enter in the input does the same. With the input empty or only spaces, the call has no `q` key. The typed text is trimmed. The semester select keeps its value. After selecting another semester, the last applied search text is kept in the call (`{ semester: "114-2", q: "cs" }`). | frontend |
| FR2-UI5 | Given `fetchCourses` resolves `{semester:{code:"115-1",...},courses:[]}`, then `[data-testid="courses-empty"]` has text `No courses found.` and `courses-table` has no `course-row-*` rows. | frontend |
| FR2-UI6 | Given `fetchCourses` resolves `{semester:null,courses:[]}`, then `[data-testid="courses-empty"]` has text `No semester has been selected.`, and the select has an extra first option with value `""` and text `Select a semester`, which is selected. | frontend |
| FR2-UI7 | While `fetchCourses` is pending, `[data-testid="courses-loading"]` is shown. When it rejects with `new ApiError(500, "An unexpected error occurred.")` (or any non-401 error), then `[data-testid="courses-error"]` has text `Unable to load courses. Please try again later.` and the app shell stays visible. When it rejects with `new ApiError(401, "Authentication required.")`, then `[data-testid="login-card"]` appears. | frontend |
| FR2-UI8 | API client, with `fetch` stubbed and the real `api/courses` module. `fetchCourses({})` calls `fetch` with `"/api/courses"`. `fetchCourses({semester:"114-2", q:"cs"})` calls `fetch` with `"/api/courses?semester=114-2&q=cs"`. `q` is URL-encoded (`q:"a b"` gives `q=a+b` or `q=a%20b`). An empty or undefined `semester` or `q` is omitted. `fetchSemesters()` calls `"/api/semesters"`. Each resolves the parsed JSON. | frontend |
| FR3-UI1 | Given the listing fixture, then for id 1 `[data-testid="register-btn-1"]` is a button with text `Register` that is enabled. When it is clicked and `registerForCourse` resolves `{message:"Successfully registered for CS101.",registrationId:10,courseId:1,courseCode:"CS101",availableSeats:49}`, then `registerForCourse` was called once with exactly `(1)`, `[data-testid="register-success"]` has text `Successfully registered for CS101.`, `course-seats-1` becomes `49`, `[data-testid="registered-tag-1"]` (text `Registered`) appears, and `register-btn-1` becomes disabled. | frontend |
| FR3-UI2 | Given the fixture (MA101 is `registered: true`), then `registered-tag-3` has text `Registered` and `register-btn-3` is disabled. `registered-tag-1` and `registered-tag-2` are not in the document. Clicking the disabled `register-btn-3` does not call `registerForCourse`. | frontend |
| FR3-UI3 | While `registerForCourse` is pending, `register-btn-1` is disabled (antd `loading`), and clicking it again does not call `registerForCourse` a second time. Other rows' buttons stay enabled. | frontend |
| FR3-UI4 | When `registerForCourse` rejects with `new ApiError(409, "Course CS101 is full.")`, then `[data-testid="register-error"]` has text `Course CS101 is full.`, `register-success` is not in the document, and `fetchCourses` is called again (a second call with the current filters) to refresh seats and registered flags. The same applies to any other 4xx `ApiError`, which shows its own message (for example `404` `Course not found.`). | frontend |
| FR3-UI5 | When `registerForCourse` rejects with a non-`ApiError` or an `ApiError` with status 5xx, then `register-error` has text `Unable to register. Please try again later.` and no refetch happens. When it rejects with `new ApiError(401, "Authentication required.")`, then `[data-testid="login-card"]` appears. | frontend |
| FR3-UI6 | The feedback alert (`register-success` or `register-error`) is removed when the user starts a new registration, clicks Search, or changes the semester. At most one of the two is shown at a time. | frontend |
| FR3-UI7 | A course with `availableSeats` 0 and `registered: false` still has an enabled `Register` button and shows seats `0`. Clicking it calls `registerForCourse` (the server's `Course {code} is full.` message is shown). FR6 will refine this. | frontend |
| FR3-UI8 | API client with `fetch` stubbed, `document.cookie` holding `XSRF-TOKEN=abc123`, and the real `api/courses` module. `registerForCourse(1)` calls `fetch("/api/registrations", init)` with `method` `"POST"`, `credentials` `"same-origin"`, `headers["Content-Type"]` `"application/json"`, `headers["X-XSRF-TOKEN"]` `"abc123"`, and `body` `JSON.stringify({courseId:1})`. A 201 response resolves the parsed JSON. A 409 with `{"message":"X"}` rejects with `ApiError(409, "X")`. | frontend |
| NFR5-AC1 | Fewer than 5 clicks. Given a logged-in user at `/dashboard` and the common mocks, the flow "click `Courses` in the side nav, then click `register-btn-1`" (2 clicks) ends with `register-success` showing `Successfully registered for CS101.`. The test counts its `user.click` calls and asserts the count is less than 5. | frontend |
| NFR5-AC2 | No confirmation step. After the single click on `register-btn-1`, `registerForCourse` has been called, and no element with role `dialog` and no antd `Modal` or `Popconfirm` is in the document. | frontend |
| FR1-UI11 (changed) | The FR1 criterion is replaced. Given an authenticated user on `/dashboard`, then `app-title` and `user-name` are as before, and `side-nav` contains exactly two `menuitem`s with texts `Dashboard` and `Courses`. The tester updates `DashboardPage.test.tsx` (or wherever FR1-UI11 lives). The coder does not touch tests. | frontend |

Test-data rules (backend):
- No seed change. SK-AC3 stays exactly as is (students 3, semesters 2, courses 4, registrations 1, registration periods 2, audit logs 0). SK-AC3 only holds if every test that writes data restores it.
- MockMvc tests that register must be `@Transactional` (rolled back), or use `@DirtiesContext(classMode = AFTER_CLASS)`.
- Concurrency tests (FR3-EX12/13/14) and spy tests (FR3-EX8/10/11) must **not** be `@Transactional`. Threads need their own transactions, and the service's retry uses its own transactions. These classes use `@DirtiesContext(classMode = AFTER_CLASS)` and create their own courses through `CourseRepository` (for example `new Course("LAST1","Last Seat Test",1,"Prof. Test",1,1,semester115_1)`), so no seed course is mutated.
- Override the clock with `@MockitoBean Clock` or a `@Primary` test `Clock`, as in FR1.
- Spy beans: `@MockitoSpyBean CourseRepository` and `AuditLogRepository`. The service must call exactly `courseRepository.saveAndFlush(course)` for the seat update (§3.2), so the spy can intercept it.

## 3. Contract

### 3.1 Domain / data model
No new entities or columns. `Course` already has `instructor`, `capacity`, `availableSeats`, `semester` and `@Version`. `Registration` already has the unique constraint `(student_id, course_id)`. `AuditLog` and `AuditAction.REGISTER` already exist.

Repository additions (names are the coder's choice, and tests must not call them directly). They are listed so the behaviour is clear:
- `SemesterRepository`: list all ordered by `code` descending.
- `CourseRepository`: find by semester code, with an optional filter on `lower(courseCode) like %q% or lower(courseName) like %q%` (with `\` escaping of `%`, `_` and `\`), ordered by `courseCode` ascending. A single query (no N+1) for the search.
- `RegistrationRepository`: the course ids a student has registered for in a given semester (single query), and an existence check for (studentId, courseId).

Seed data: unchanged (no new rows). Tests that need extra courses create them (§2 test-data rules).

### 3.2 Service layer
```java
// package edu.tku.registration.service
public record SemesterOption(String code, String name, boolean current) { }
public record CourseSummary(Long id, String code, String name, int credits, String instructor,
                            int capacity, int availableSeats, boolean registered) { }
public record CourseListing(SemesterOption semester, List<CourseSummary> courses) { }   // semester null if none selected

@Service
public class CourseService {
    public CourseService(CourseRepository courseRepository, SemesterRepository semesterRepository,
                         RegistrationRepository registrationRepository);

    /** Read-only transaction. Semesters ordered by code descending. */
    public List<SemesterOption> listSemesters();

    /** Read-only transaction.
     *  semesterCode null/blank -> the semester with current=true; if none, returns (null, List.of()).
     *  semesterCode given     -> that semester; throws SemesterNotFoundException if unknown.
     *  query: trimmed; null/blank = no filter; otherwise case-insensitive substring match on courseCode OR
     *  courseName, with % and _ treated literally.
     *  Only courses of the selected semester are returned, sorted by courseCode ascending.
     *  registered = the student has a Registration for the course. */
    public CourseListing searchCourses(String studentId, String semesterCode, String query);
}

public record RegistrationResult(Long registrationId, Long courseId, String courseCode,
                                 String courseName, int availableSeats, LocalDateTime registrationDate) { }

@Service
public class RegistrationService {
    public RegistrationService(StudentRepository studentRepository, CourseRepository courseRepository,
                               RegistrationRepository registrationRepository,
                               AuditLogRepository auditLogRepository,
                               PlatformTransactionManager transactionManager, Clock clock);

    /** NOT annotated @Transactional itself. Runs up to MAX_ATTEMPTS = 5 attempts; each attempt is one
     *  new transaction (TransactionTemplate, REQUIRED). An attempt is retried only when it throws
     *  org.springframework.dao.ConcurrencyFailureException (optimistic or pessimistic lock failure). Any other exception propagates at once.
     *  After 5 failed attempts throws RegistrationConflictException.
     *
     *  One attempt, in this order, all in one transaction:
     *   1. studentRepository.findByStudentId(studentId) else StudentNotFoundException(studentId)
     *   2. courseRepository.findById(courseId) else CourseNotFoundException (also when courseId is null/0)
     *   3. if a Registration for (student, course) exists -> AlreadyRegisteredException(courseCode)
     *   4. if course.availableSeats <= 0 -> CourseFullException(courseCode)       [minimal guard; FR6 refines]
     *   5. course.setAvailableSeats(course.getAvailableSeats() - 1); courseRepository.saveAndFlush(course)
     *      (the @Version check happens here)
     *   6. registrationRepository.saveAndFlush(new Registration(student, course, LocalDateTime.now(clock)));
     *      a DataIntegrityViolationException here is translated to AlreadyRegisteredException(courseCode)
     *   7. auditLogRepository.save(new AuditLog(studentId, course.getId(), AuditAction.REGISTER, LocalDateTime.now(clock)))
     *  Any exception rolls the whole attempt back (seats, registration, audit). No audit row on failures. */
    public RegistrationResult register(String studentId, Long courseId);
}
```
The registration period is **not** checked (FR7). The semester is **not** checked (any course may be registered).

Exceptions (package `edu.tku.registration.service`, all extend `RuntimeException`). `getMessage()` is exactly the user-facing text, and the handler uses it:
| Class | Constructor | `getMessage()` | HTTP |
|---|---|---|---|
| `SemesterNotFoundException` | `(String code)` | `Semester not found.` | 404 |
| `CourseNotFoundException` | `(Long courseId)` | `Course not found.` | 404 |
| `AlreadyRegisteredException` | `(String courseCode)` | `You are already registered for {courseCode}.` | 409 |
| `CourseFullException` | `(String courseCode)` | `Course {courseCode} is full.` | 409 |
| `RegistrationConflictException` | `()` | `The course was just updated by another student. Please try again.` | 409 |

`StudentNotFoundException` already exists, with API text `Student not found.` and 404.

### 3.3 Web layer
DTOs (records, package `edu.tku.registration.web`): `RegistrationRequest(Long courseId)`, `RegistrationResponse(String message, Long registrationId, Long courseId, String courseCode, int availableSeats)`. The `CourseListing`, `CourseSummary` and `SemesterOption` records are serialised as they are. JSON property names equal the record component names. Unknown request properties are ignored (Boot default).

Controllers: `CourseController` (`GET /api/semesters`, `GET /api/courses`) and `RegistrationController` (`POST /api/registrations`). Both take the student id from `Principal.getName()` and never from the request. `ApiExceptionHandler` gains the five mappings from §3.2, each returning `ApiError(ex.getMessage())` with the listed status. A null `courseId` in the request (the field is missing or null) gives 400 `Course ID is required.`, handled in the controller before the service call. Unreadable bodies keep the existing 400 `Malformed request.`

REST API (all under `/api`, JSON in/out, CSRF header required on POST, ROLE_STUDENT required):
| Method | Path | Request | Success | Failures |
|--------|------|---------|---------|----------|
| GET | /api/semesters | — | 200 `[{"code":"115-1","name":"2026 Fall","current":true}, …]` | 401 `Authentication required.` |
| GET | /api/courses | optional query `semester` (semester code), `q` (search text) | 200 `{"semester":{"code","name","current"} or null,"courses":[{"id","code","name","credits","instructor","capacity","availableSeats","registered"}]}` | 401 `Authentication required.` · 404 `Semester not found.` |
| POST | /api/registrations | `{"courseId": 1}` | 201 `{"message":"Successfully registered for CS101.","registrationId":10,"courseId":1,"courseCode":"CS101","availableSeats":49}` | 400 `Course ID is required.` · 400 `Malformed request.` · 401 `Authentication required.` · 403 `Invalid or missing CSRF token.` · 404 `Course not found.` · 404 `Student not found.` · 409 `You are already registered for {code}.` · 409 `Course {code} is full.` · 409 `The course was just updated by another student. Please try again.` |

`GET /api/courses` example for Alice (defaults):
```json
{
  "semester": { "code": "115-1", "name": "2026 Fall", "current": true },
  "courses": [
    { "id": 1, "code": "CS101", "name": "Introduction to Programming", "credits": 3, "instructor": "Prof. Lee",   "capacity": 50, "availableSeats": 50, "registered": false },
    { "id": 2, "code": "CS201", "name": "Data Structures",             "credits": 3, "instructor": "Prof. Chang", "capacity": 40, "availableSeats": 40, "registered": false },
    { "id": 3, "code": "MA101", "name": "Calculus I",                  "credits": 4, "instructor": "Prof. Huang", "capacity": 1,  "availableSeats": 0,  "registered": false }
  ]
}
```
(The ids are illustrative. Tests read real ids from the repository.)

Frontend routes:
| Route | Page component | Auth | What it shows / does |
|-------|----------------|------|----------------------|
| `/courses` | `ProtectedRoute` → `AppLayout` → `CoursesPage` | yes | Semester selector, search box, course table, register buttons. |

The server-side SPA forward for `/courses` already exists (FR1-EX16).

### 3.4 User-facing messages (exact text)
Backend (JSON `message`):
| Key | Text |
|-----|------|
| register.success | Successfully registered for {courseCode}. |
| register.duplicate | You are already registered for {courseCode}. |
| register.full | Course {courseCode} is full. |
| register.conflict | The course was just updated by another student. Please try again. |
| register.courseIdRequired | Course ID is required. |
| course.notFound | Course not found. |
| semester.notFound | Semester not found. |

(`Malformed request.`, `Student not found.`, `Authentication required.` and `Invalid or missing CSRF token.` are from FR1.)

Frontend:
| Key | Text |
|-----|------|
| nav.courses | Courses |
| courses.title | Available Courses |
| courses.semester.label | Semester |
| courses.semester.option | {name} ({code}) |
| courses.semester.placeholder | Select a semester |
| courses.search.placeholder | Search by course code or name |
| courses.search.button | Search |
| courses.columns | Code / Name / Credits / Instructor / Available Seats / Action |
| courses.register.button | Register |
| courses.registered.tag | Registered |
| courses.empty | No courses found. |
| courses.empty.noSemester | No semester has been selected. |
| courses.error | Unable to load courses. Please try again later. |
| courses.register.error.generic | Unable to register. Please try again later. |
| courses.register.success | the server's `message`, shown as is |
| courses.register.error.4xx | the server's `ApiError.message`, shown as is |

### 3.5 Frontend structure
New or changed files (tests import these exact paths):
| File | Exports |
|---|---|
| `src/api/types.ts` (extended) | `SemesterOption { code: string; name: string; current: boolean }`; `CourseSummary { id: number; code: string; name: string; credits: number; instructor: string; capacity: number; availableSeats: number; registered: boolean }`; `CourseListing { semester: SemesterOption \| null; courses: CourseSummary[] }`; `RegistrationResult { message: string; registrationId: number; courseId: number; courseCode: string; availableSeats: number }` |
| `src/api/courses.ts` | `fetchSemesters(): Promise<SemesterOption[]>`; `fetchCourses(params: { semester?: string; q?: string }): Promise<CourseListing>`; `registerForCourse(courseId: number): Promise<RegistrationResult>`. All use `apiFetch`. The query string is built with `URLSearchParams` in the order `semester`, `q`, and empty values are omitted. `registerForCourse` is `apiFetch("/api/registrations", { method: "POST", body: { courseId } })`. |
| `src/pages/CoursesPage.tsx` | `CoursesPage` |
| `src/components/AppLayout.tsx` (changed) | `NAV_ITEMS` gets a second item `{ key: "/courses", label: "Courses", icon: BookOutlined }` after Dashboard. |
| `src/App.tsx` (changed) | adds `<Route path="/courses" element={<CoursesPage/>}/>` inside the protected `AppLayout` route. |

`CoursesPage` behaviour:
- On mount it calls `fetchSemesters()` once and `fetchCourses({})` once (concurrently). It shows `courses-loading` until the listing arrives. If `fetchSemesters` fails, the select shows only the listing's semester (the page does not fail because of it).
- State: `semester` (the listing's `semester.code`, or `""`), `appliedQuery` (the last submitted, trimmed search text). Changing the semester calls `fetchCourses({ semester, q? })`. Search calls `fetchCourses({ semester?, q? })`. `semester` is omitted only while none is selected, and `q` is omitted when blank. Each call replaces the listing (showing `courses-loading` or keeping the old table. This is not asserted, so the coder may choose).
- Register click: clears the feedback and marks that row as pending. It then calls `registerForCourse(course.id)`.
  - Success: the row's `availableSeats` is set from the response, `registered` becomes true, and `register-success` shows the response `message`. No refetch.
  - `ApiError` 401: `markAnonymous()`.
  - Other `ApiError` 4xx: `register-error` shows the `message`, then it refetches the listing with the current filters (feedback stays).
  - Anything else (5xx, network): `register-error` shows `Unable to register. Please try again later.`, with no refetch.
- A registered row has a disabled `Register` button and a `Registered` tag. The Register button has no confirm step.
- Tests mock `../api/courses`, so components never call `fetch` directly.

### 3.6 UI elements tests rely on
Visual direction: reuse the FR1 shell and navy theme. Use an antd `Card`, `Table` (pagination off), `Input`, `Button`, `Tag` and `Alert`.

| data-testid | Element | Content / notes |
|---|---|---|
| courses-title | `<h2>` (`Typography.Title level={2}`) | `Available Courses` |
| semester-select | native `<select>` with `<label>` `Semester` | options `{name} ({code})`, value = semester code. Placeholder option (value `""`, text `Select a semester`) only when no semester is selected. |
| course-search-input | the real `<input>` | placeholder `Search by course code or name`. Enter submits. |
| course-search-button | `<button>` | text `Search` |
| courses-loading | loading wrapper (`Spin`) | |
| courses-error | `Alert type="error"` | `Unable to load courses. Please try again later.` |
| courses-empty | element holding the empty text | `No courses found.` or `No semester has been selected.` |
| courses-table | wrapper of the antd `Table` | headers `Code`, `Name`, `Credits`, `Instructor`, `Available Seats`, `Action` |
| course-row-{id} | the `<tr>` (via `onRow`) | one per course, in API order |
| course-code-{id} / course-name-{id} / course-credits-{id} / course-instructor-{id} / course-seats-{id} | `<span>` in the cell | exact value text only (numbers as plain digits) |
| register-btn-{id} | `<button>` | text `Register`. Disabled when registered or pending. `loading` while pending. |
| registered-tag-{id} | antd `Tag` (green) | text `Registered`, rendered only when `registered` is true |
| register-success | `Alert type="success"` | the server `message`, closable not required |
| register-error | `Alert type="error"` | error text |

The table is not shown while `courses-empty` is shown. If an antd component does not forward `data-testid`, wrap it. Buttons and inputs carry it on the real element.

### 3.7 Test harness assumptions
- `vi.mock("../api/courses")` (relative to `src/__tests__/`) replaces `fetchSemesters`, `fetchCourses` and `registerForCourse`. `fetchMe`, `fetchDashboard` and `logout` are mocked as in FR1.
- `render(<MemoryRouter initialEntries={["/courses"]}><App/></MemoryRouter>)` works with no other providers.

## 4. Assumptions & decisions
1. **API shape:** `GET /api/courses` returns `{semester, courses}` rather than a bare array, so the client knows which semester was selected by default. `semester` is JSON null when none is current. The template's `/api/courses` example (`code`, …) is followed, using `code`, `name` and `credits` as JSON names for `courseCode`, `courseName` and `creditHours`.
2. **Selected semester:** the `semester` query param is the semester code. When it is absent, the current semester (`current=true`) is used. If none is current and none is given, the result is `semester: null` with an empty list (200, not an error). An unknown code gives 404 `Semester not found.`
3. **Semester selector** is a native `<select>`. It is reliable in jsdom, and antd `Select` renders options in a portal with virtual lists, which is brittle to test. Styling can use antd classes.
4. **Search:** a trimmed, case-insensitive substring match on code OR name. `%` and `_` are literal. Search runs on clicking Search or pressing Enter (no debounce or live search), so tests need no timers. The search text is kept when the semester changes.
5. **Ordering:** courses by code ascending, semesters by code descending (the newest first).
6. **"Registered" indicator:** the listing carries a per-student `registered` boolean. There is no `GET /api/registrations` yet. FR5 adds that and the schedule page. The path `/api/registrations` stays 401 for anonymous callers (NFR2-AC2) and 404 for authenticated GETs until FR5.
7. **Student identity** comes from `Principal.getName()` (security context). A `studentId` in the body is ignored, not rejected.
8. **Minimal seat guard (FR6 will refine):** `availableSeats <= 0` gives 409 `Course {code} is full.` It is enforced in the service inside the transaction, so seats can never go below 0. FR6 may change the wording, add a capacity-vs-registrations consistency check and UI treatment. The UI keeps the Register button enabled on full courses, so the server message is what the user sees, and FR6 will decide whether to disable it.
9. **Check order:** student → course → duplicate → full. A student already registered for a full course gets `already registered`, not `full`.
10. **Registration period is not enforced** (FR7). Any course of any semester can be registered at any time, including `114-2` and outside the period. FR7 will add the check, reusing `SemesterService`. Its tests will need a fixed Clock.
11. **Optimistic-lock conflict:** `@Version` on Course makes a stale seat update fail with `ObjectOptimisticLockingFailureException`. The service retries the whole attempt (fresh transaction, fresh read) up to 5 times on any `ConcurrencyFailureException`. A retry re-evaluates the duplicate and full checks, so the loser of a last-seat race ends with `Course {code} is full.` The retries are needed so that with N seats and more than N contenders exactly N succeed. If all 5 attempts conflict, the result is 409 `The course was just updated by another student. Please try again.` Tests accept either `CourseFullException` or `RegistrationConflictException` for the loser, because the exact interleaving (and H2 lock timeouts) is not deterministic. The invariants are what is asserted.
12. **Transaction design:** `RegistrationService.register` is not itself `@Transactional`. It uses a `TransactionTemplate`, so each retry gets its own transaction (a `@Transactional` method can't retry itself in-process). In a rolled-back `@Transactional` MockMvc test the attempt joins the test transaction, which is fine for non-conflict paths only.
13. **Duplicate race:** a `DataIntegrityViolationException` from the unique constraint is translated to `AlreadyRegisteredException`. Normally the course version conflict plus the retry catches a double submit first.
14. **Audit (NFR4):** `AuditLog(studentId, courseId, REGISTER, now(clock))` is written in the same transaction as the seat change and the registration. Only successful registrations are logged. Rejected attempts (duplicate, full, not found) are not logged, because the requirement says "registrations and drops are logged". `registrationDate` and the audit timestamp use `LocalDateTime.now(clock)` in Asia/Taipei.
15. **No seed changes.** The four seed courses are enough: CS101/CS201 (searchable, registerable), MA101 (full, registered by Carol), CS102 (other semester). Tests create extra courses (`LAST1`, `PF0001`…) themselves and clean up, so SK-AC3's counts stay `3 / 2 / 4 / 1 / 2 / 0`.
16. **NFR1 (search under 3 s):** verified with 1,000 courses in one semester through MockMvc. The search is a single indexed-by-semester SQL query with no N+1, and `registered` flags come from one extra query. There is no pagination in Phase 1.
17. **NFR5 (fewer than 5 clicks):** the path from the dashboard is 2 clicks (Courses → Register), and there is no confirmation dialog. Typing is not counted as a click. Counting the Log in click, the whole path from the login page is 3 clicks (Log in, Courses, Register).
18. **Success feedback** is an inline alert (not an antd `message` toast), so it is testable without portals or timers. It stays until the next action.
19. **Local row update on success** (seats from the response, `registered = true`, no refetch). After a 4xx failure the page refetches, because the displayed seats or flags may be stale.
20. **FR1-UI11 changes** (the sidebar now has two items), and the tester updates that test. No other FR1 criterion changes. `FR1-EX16` (`/courses/123` forwards to the SPA) is unaffected.
21. **Dashboard** is unchanged (no registered-courses count; FR5).
22. **Request validation:** only a missing/null `courseId` gives 400 `Course ID is required.` Non-numeric JSON gives 400 `Malformed request.` A non-positive or unknown id gives 404 `Course not found.`
23. **Frontend filtering** is server-side only. The client never filters the list itself.

## 5. Out of scope
- FR4 (drop), FR5 (schedule page, total credits, print/download, `GET /api/registrations`), FR6 (full capacity messaging, seat UI treatment) and FR7 (registration-period enforcement).
- Time-conflict checks, credit-limit checks, prerequisites, waitlists and course categories or departments.
- Pagination, sorting controls and filters other than the search text and semester.
- Admin course management and real-time seat updates (no polling or websockets).
- Dashboard changes, i18n, and browser end-to-end tests.
