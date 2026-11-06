# Tamkang Course Registration System

Student course registration web app (Phase 1). Requirements: `docs/requirements/phase1.md`.
Feature specs (one per feature, written by the planner agent): `docs/specs/`.

## Tech stack
- Java 21 language level (JDK 23 installed locally), Spring Boot 3.4.x, Maven
- Spring Web + Thymeleaf (server-rendered UI), Spring Security (form login), Spring Data JPA
- H2 in-memory DB for dev and tests; seed data in `src/main/resources/data.sql`
- Tests: JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc, spring-security-test

## Commands
- Build + run all tests: `mvn -q test`
- Run one test class: `mvn -q test -Dtest=RegistrationServiceTest`
- Run the app: `mvn spring-boot:run` → http://localhost:8080

## Code layout
Base package `edu.tku.registration`:
- `domain` — JPA entities (Student, Course, Semester, Registration, AuditLog, RegistrationPeriod)
- `repository` — Spring Data repositories
- `service` — business rules (all validation lives here, not in controllers)
- `web` — controllers + form/DTO classes; templates in `src/main/resources/templates`
- `security` — Spring Security config, user details service
- `config` — other configuration (e.g. a `java.time.Clock` bean)

## Conventions (all agents must follow)
- **Time:** never call `LocalDateTime.now()` directly. Inject `java.time.Clock` so registration-period rules are testable.
- **Seat counts:** decrement/increment `availableSeats` in the same transaction as the registration change. Use optimistic locking (`@Version`) on `Course` so two students can't take the last seat.
- **Passwords:** BCrypt only. Never log passwords or put them in seed data as plain text.
- **Audit (NFR4):** every register and drop writes an `AuditLog` row (studentId, courseId, action, timestamp).
- **User-facing messages:** use the exact strings defined in the feature spec. Tests assert on them.
- **Errors:** business-rule failures throw a specific exception (e.g. `CourseFullException`) that the controller turns into a message. No stack traces shown to users.

## Agent rules
- The **coder** never edits files under `src/test/`.
- The **tester** never edits files under `src/main/`.
- If one of them thinks the other's code is wrong, it reports that instead of "fixing" it.
- Each acceptance criterion in a spec has an ID (e.g. `FR3-AC5`). Every ID has at least one test, and the test's `@DisplayName` starts with that ID.
- Pipeline working files (test reports, review notes) go in `.pipeline/<feature-id>/`, which is gitignored.
