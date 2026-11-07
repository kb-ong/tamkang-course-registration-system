# Tamkang Course Registration System

Student course registration web app (Phase 1). Requirements: `docs/requirements/phase1.md`.
Feature specs (one per feature, written by the planner agent): `docs/specs/`.

## Tech stack
- Java 21 language level (JDK 23 installed locally), Spring Boot 3.4.x, Maven
- **Backend:** Spring Web as a JSON REST API under `/api/**`, Spring Security (session cookie + CSRF cookie token, JSON login endpoint), Spring Data JPA
- **Frontend:** React 18 + TypeScript SPA in `frontend/` (Vite, Ant Design 5, React Router). Built by `frontend-maven-plugin` into the jar's `static/`, so the app is still one jar on port 8080. Spring forwards non-API, non-asset routes to `index.html`.
- H2 in-memory DB for dev and tests; seed data in `src/main/resources/data.sql`
- Backend tests: JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc (JSON assertions), spring-security-test
- Frontend tests: Vitest + React Testing Library + jsdom (API calls mocked, e.g. with MSW or `vi.fn` fetch)
- UI look: a generic, professional university portal (no real Tamkang logos or trademarks)

## Commands
- Build + run all tests (backend + frontend): `mvn -q test`
- Frontend only: `cd frontend && npm test` (Vitest) / `npm run dev` (Vite on :5173, proxies `/api` to :8080)
- Backend tests only (fast, skips the npm build and Vitest): `mvn -q test -Dskip.npm -Dskip.installnodenpm`
- Run one backend test class: `mvn -q test -Dskip.npm -Dskip.installnodenpm -Dtest=RegistrationServiceTest`
- Run the app: `mvn spring-boot:run` → http://localhost:8080

## Code layout
Base package `edu.tku.registration`:
- `domain` — JPA entities (Student, Course, Semester, Registration, AuditLog, RegistrationPeriod)
- `repository` — Spring Data repositories
- `service` — business rules (all validation lives here, not in controllers)
- `web` — REST controllers + request/response DTOs (records), plus the SPA forward controller and a `@RestControllerAdvice` that turns business exceptions into JSON errors
- `security` — Spring Security config, user details service
- `config` — other configuration (e.g. a `java.time.Clock` bean)

Frontend `frontend/src/`: `api/` (typed fetch client, CSRF handling), `pages/`, `components/`, `auth/` (auth context, protected routes).

## Conventions (all agents must follow)
- **Time:** never call `LocalDateTime.now()` directly. Inject `java.time.Clock` so registration-period rules are testable.
- **Seat counts:** decrement/increment `availableSeats` in the same transaction as the registration change. Use optimistic locking (`@Version`) on `Course` so two students can't take the last seat.
- **Passwords:** BCrypt only. Never log passwords or put them in seed data as plain text.
- **Audit (NFR4):** every register and drop writes an `AuditLog` row (studentId, courseId, action, timestamp).
- **User-facing messages:** use the exact strings defined in the feature spec. Tests assert on them.
- **Errors:** business-rule failures throw a specific exception (e.g. `CourseFullException`) that the `@RestControllerAdvice` turns into a JSON error body `{ "message": "<exact spec text>" }` with the HTTP status the spec defines. The UI displays that message. No stack traces in responses or the UI.

## Agent rules
- **Test files** = `src/test/**`, `frontend/src/__tests__/**` and `frontend/src/test-setup.ts`. **Production files** = everything else (`src/main/**`, the rest of `frontend/`, `pom.xml`, `frontend/package.json`, Vite/Vitest config).
- The **coder** never edits test files. The **tester** never edits production files (if a test needs a dev dependency, the tester asks via the orchestrator).
- Frontend tests: every `it`/`test` name starts with the criterion ID, same as `@DisplayName` on the backend.
- If one of them thinks the other's code is wrong, it reports that instead of "fixing" it.
- Each acceptance criterion in a spec has an ID (e.g. `FR3-AC5`). Every ID has at least one test, and the test's `@DisplayName` starts with that ID.
- Pipeline working files (test reports, review notes) go in `.pipeline/<feature-id>/`, which is gitignored.
