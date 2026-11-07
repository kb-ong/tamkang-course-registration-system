# Spec: FR1-login — Student Login (React SPA + JSON API), project skeleton, data model, NFR2 security

Requirements covered: FR1 (AC1–AC3), NFR2, plus the project skeleton, core data model, seed data, Clock bean and frontend build (build-order step 1)
Status: Draft, revision 2 (React + Ant Design SPA replaces Thymeleaf). Needs re-approval at the /build-feature human gate.

## 1. Summary
After this feature ships, `mvn -q test` builds and tests both the Spring Boot backend and the React frontend, and `mvn spring-boot:run` serves the whole app from one jar on :8080. A student opens a university-portal-style login page and signs in with their student ID and password. Bad credentials show an error. After signing in they land in an app shell (header, user area, sidebar) showing a dashboard with a welcome message, their profile card and a current-semester card. They can log out. Every `/api/**` endpoint except login and the CSRF bootstrap requires an authenticated student and returns 401 JSON otherwise. All Phase 1 entities, repositories, seed data and the `Clock` bean exist, so later features only add behaviour.

**Build slices.** This is one spec, but it can be built in two passes (see §4 assumption 30). **Slice A (backend):** §3.0–§3.4 and §3.6, criteria marked *backend*. **Slice B (frontend):** §3.5–§3.8, criteria marked *frontend*.

## 2. Acceptance criteria

Test levels:
- **backend**: JUnit 5 under `src/test/java`. For HTTP, use `@SpringBootTest` + `@AutoConfigureMockMvc` against real seed data. For beans, call them directly from a `@SpringBootTest`. Use `spring-security-test` where useful (`csrf()`, `user(...)`, `authenticated()`, `unauthenticated()`).
- **frontend**: Vitest + React Testing Library + jsdom under `frontend/src/__tests__/`. API modules are mocked with `vi.mock` (see §3.8). The `it`/`test` name starts with the criterion ID.

"JSON error `X`" means: `Content-Type` compatible with `application/json`, and the body is exactly `{"message":"X"}`.

### FR1: Student Login
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR1-AC1 | Given seed student `410000001` / `password123`, when `POST /api/auth/login` is sent with JSON body `{"studentId":"410000001","password":"password123"}`, `Content-Type: application/json` and a valid CSRF token, then status is 200, the body is exactly `{"studentId":"410000001","name":"Alice Chen","email":"410000001@o365.tku.edu.tw"}`, the result is `authenticated().withUsername("410000001")`, and `GET /api/auth/me` using the returned `MockHttpSession` returns 200. | backend |
| FR1-AC2a | Given student `410000001`, when logging in with password `wrong-password` (valid CSRF), then status is 401 with JSON error `Invalid student ID or password.`, and the result is `unauthenticated()`. | backend |
| FR1-AC2b | Given no student has ID `499999999`, when logging in with that ID and `password123` (valid CSRF), then status is 401 with JSON error `Invalid student ID or password.`, and the result is `unauthenticated()`. | backend |
| FR1-AC2c | Given `login()` from `api/auth` rejects with `new ApiError(401, "Invalid student ID or password.")`, when the user enters `410000001` / `wrong-password` on `/login` and clicks **Log in**, then `[data-testid="login-error"]` appears with text `Invalid student ID or password.`, and the login card is still shown (the user stays on `/login`). | frontend |
| FR1-AC3 | Given the `Clock` is overridden to the fixed instant `2026-09-01T09:00:00+08:00` (zone `Asia/Taipei`) and the request is authenticated as `410000001` with role `STUDENT`, when `GET /api/dashboard`, then status is 200 and the body is exactly the §3.4 example: `student` = Alice Chen's profile, `currentSemester` = `{"code":"115-1","name":"2026 Fall","registrationStartAt":"2026-08-01T00:00:00","registrationEndAt":"2026-12-31T23:59:59","registrationOpen":true}`. | backend |
| FR1-EX1 | Given `fetchMe()` resolves `null`, when the app is rendered at `/login` with no router state, then `[data-testid="login-card"]` is shown, and neither `[data-testid="login-error"]` nor `[data-testid="logout-message"]` is in the document. | frontend |
| FR1-EX2 | When `POST /api/auth/login` is sent (valid CSRF) with each of these bodies: `{"studentId":"","password":""}`, `{}`, `{"studentId":"410000001"}`, then each response is 401 with JSON error `Invalid student ID or password.` and the result is `unauthenticated()`. | backend |
| FR1-EX3 | Given a session obtained from a successful login as `410000001`, when `POST /api/auth/logout` is sent with that session and a valid CSRF token, then status is 204 with an empty body, and a following `GET /api/auth/me` with the same session returns 401 with JSON error `Authentication required.` | backend |
| FR1-EX4 | Given the seed data, every `Student.passwordHash` starts with `$2a$`, `$2b$` or `$2y$`, is not equal to `password123`, and `passwordEncoder.matches("password123", hash)` is `true`. | backend |
| FR1-EX5 | When `StudentService.getProfile("499999999")` is called, then `StudentNotFoundException` is thrown with message `Student not found: 499999999`. | backend |
| FR1-EX6 | The JSON bodies of a successful `POST /api/auth/login`, `GET /api/auth/me` and `GET /api/dashboard` (as `410000001`) do not contain the substring `$2` or the key `passwordHash`. Each profile object has exactly the keys `studentId`, `name`, `email`. | backend |
| FR1-EX7 | When `GET /` is sent anonymously and again as an authenticated student, then each response is 200 with `forwardedUrl("/index.html")`. | backend |
| FR1-EX8 | Given the request is authenticated as `user("410000001").roles("STUDENT")`, when `GET /api/auth/me`, then status is 200 and the body is exactly `{"studentId":"410000001","name":"Alice Chen","email":"410000001@o365.tku.edu.tw"}`. | backend |
| FR1-EX9 | When `GET /api/auth/csrf` is sent anonymously with no cookies, then status is 204, and the response sets cookie `XSRF-TOKEN` with a non-empty value, path `/`, and `httpOnly` = false. | backend |
| FR1-EX10 | Real cookie flow, without the `csrf()` post-processor. Given cookie value `v` from FR1-EX9: (a) `POST /api/auth/login` with valid credentials, cookie `XSRF-TOKEN=v` and header `X-XSRF-TOKEN: v` returns 200; (b) the same request with header `X-XSRF-TOKEN: not-the-token` returns 403 with JSON error `Invalid or missing CSRF token.` | backend |
| FR1-EX11 | Given an existing `MockHttpSession` whose id was recorded before the request, when a successful login is performed with that session, then the session id after the request differs from the recorded id (session fixation protection). | backend |
| FR1-EX12 | When a successful login is performed, then the response sets cookie `XSRF-TOKEN` with max-age `0` (the pre-login CSRF token is invalidated). | backend |
| FR1-EX13 | Given a session from a successful login, when `POST /api/auth/logout` is sent with that session but **without** a CSRF token, then status is 403 with JSON error `Invalid or missing CSRF token.`, and `GET /api/auth/me` with the same session still returns 200. | backend |
| FR1-EX14 | When `POST /api/auth/login` is sent (valid CSRF, `Content-Type: application/json`) with body `{not json`, then status is 400 with JSON error `Malformed request.` | backend |
| FR1-EX15 | Given an authenticated student, when `GET /api/does-not-exist`, then status is 404 with JSON error `Not found.` | backend |
| FR1-EX16 | When each of `GET /login`, `GET /dashboard` and `GET /courses/123` is sent anonymously, then each response is 200 with `forwardedUrl("/index.html")`. Requests to `GET /api` and `GET /api/anything` are never forwarded to `/index.html`. | backend |
| FR1-EX17 | Given `new SemesterService(semesterRepository, registrationPeriodRepository, clock)` with `clock = Clock.fixed(<instant>, ZoneId.of("Asia/Taipei"))`, then `getCurrentSemesterInfo().get().registrationOpen()` is `true` at local `2026-08-01T00:00:00` and at `2026-12-31T23:59:59`, and `false` at `2026-07-31T23:59:59` and at `2027-01-01T00:00:00` (both bounds are inclusive). | backend |
| FR1-EX18 | In a `@Transactional` test (rolled back): (a) with semester `115-1` set to `current=false` and flushed, `getCurrentSemesterInfo()` returns `Optional.empty()`; (b) with `115-1` set to `current=false` and a new `Semester("116-1","2027 Fall",true)` saved and flushed, the result is present with `code` `116-1`, `registrationStartAt` `null`, `registrationEndAt` `null`, and `registrationOpen` `false`. | backend |
| FR1-EX19 | Given the request is authenticated as `user("499999999").roles("STUDENT")` (no such student), when `GET /api/auth/me`, then status is 404 with JSON error `Student not found.` | backend |

### FR1: Frontend (React SPA)
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FR1-UI1 | Given `fetchMe()` resolves `null`, when the app is rendered at `/login`, then: `[data-testid="login-brand"]` has text `Tamkang Course Registration System`; `[data-testid="login-card"]` contains `Student Login`; `getByLabelText("Student ID")` and `getByLabelText("Password")` resolve to the inputs `[data-testid="login-student-id"]` and `[data-testid="login-password"]` (the latter has `type="password"`); and `[data-testid="login-submit"]` is a button with text `Log in`. | frontend |
| FR1-UI2 | Given `fetchMe()` resolves `null`, `login()` resolves Alice's profile and `fetchDashboard()` resolves the §3.4 example, when the user types `410000001` / `password123` and clicks **Log in**, then `login` was called once with exactly `("410000001", "password123")`, and `[data-testid="welcome-message"]` with text `Welcome, Alice Chen` appears (the dashboard route is shown). | frontend |
| FR1-UI3 | Given the login page, when **Log in** is clicked with both fields empty, then the texts `Please enter your student ID.` and `Please enter your password.` appear and `login` is not called. If the student ID field holds only spaces (`"   "`), `Please enter your student ID.` also appears. | frontend |
| FR1-UI4 | Given `fetchMe()` resolves `null`, when the app is rendered at `/dashboard`, then `[data-testid="login-card"]` is shown, and neither `[data-testid="welcome-message"]` is shown nor is `fetchDashboard` called. | frontend |
| FR1-UI5 | When the app is rendered at `/`: (a) if `fetchMe()` resolves Alice's profile, then `[data-testid="welcome-message"]` appears; (b) if `fetchMe()` resolves `null`, then `[data-testid="login-card"]` appears. Unknown routes (e.g. `/no-such-page`) behave the same as `/`. | frontend |
| FR1-UI6 | Given `fetchMe()` resolves Alice's profile, when the app is rendered at `/login`, then `[data-testid="welcome-message"]` appears and `[data-testid="login-card"]` is not in the document. | frontend |
| FR1-UI7 | Given `fetchMe()` returns a promise that has not resolved yet, when the app is rendered at `/dashboard`, then `[data-testid="auth-loading"]` is in the document, and neither `[data-testid="login-card"]` nor `[data-testid="welcome-message"]` is. | frontend |
| FR1-UI8 | Given an authenticated user and the §3.4 dashboard example, when `/dashboard` renders, then `[data-testid="profile-card"]` contains `Student Profile`, and the text content of `[data-testid="profile-student-id"]` is exactly `410000001`, of `[data-testid="profile-name"]` exactly `Alice Chen`, and of `[data-testid="profile-email"]` exactly `410000001@o365.tku.edu.tw`. | frontend |
| FR1-UI9 | Given the §3.4 dashboard example, then `[data-testid="semester-card"]` contains `Current Semester`, `[data-testid="semester-name"]` is exactly `2026 Fall (115-1)`, `[data-testid="registration-period"]` is exactly `2026-08-01 00:00 to 2026-12-31 23:59`, and `[data-testid="registration-status"]` is exactly `Open`. With `registrationOpen: false` it is exactly `Closed`. | frontend |
| FR1-UI10 | (a) Given `currentSemester: null`, then `[data-testid="semester-empty"]` has text `No current semester has been set.` and `[data-testid="semester-name"]` is absent. (b) Given `registrationStartAt` and `registrationEndAt` are `null` and `registrationOpen` is `false`, then `[data-testid="registration-period"]` is exactly `Not scheduled` and `[data-testid="registration-status"]` is exactly `Closed`. | frontend |
| FR1-UI11 | Given an authenticated user on `/dashboard`, then `[data-testid="app-title"]` has text `Tamkang Course Registration System`, `[data-testid="user-name"]` is exactly `Alice Chen`, and `[data-testid="side-nav"]` contains exactly one element with role `menuitem`, whose text is `Dashboard`. | frontend |
| FR1-UI12 | Given an authenticated user on `/dashboard` and `logout()` resolves, when `[data-testid="logout-button"]` (text `Log out`) is clicked, then `logout` was called once, `[data-testid="login-card"]` appears, and `[data-testid="logout-message"]` has text `You have been logged out.` The same navigation happens when `logout()` rejects. | frontend |
| FR1-UI13 | Given an authenticated user: (a) if `fetchDashboard()` rejects with `new ApiError(401, "Authentication required.")`, then `[data-testid="login-card"]` appears; (b) if it rejects with `new ApiError(500, "An unexpected error occurred.")`, then `[data-testid="dashboard-error"]` has text `Unable to load your dashboard. Please try again later.` and the app shell stays visible. | frontend |
| FR1-UI14 | API client, with `fetch` stubbed via `vi.stubGlobal` and `document.cookie` holding `XSRF-TOKEN=abc123`: when `login("410000001","password123")` from the real `api/auth` module is called and the stub returns 200 with Alice's profile JSON, then `fetch` was called with `"/api/auth/login"` and an init whose `method` is `"POST"`, `credentials` is `"same-origin"`, `headers["Content-Type"]` is `"application/json"`, `headers["X-XSRF-TOKEN"]` is `"abc123"`, and whose `body` is `JSON.stringify({studentId:"410000001",password:"password123"})`. The promise resolves to the profile. | frontend |
| FR1-UI15 | API client: given no `XSRF-TOKEN` cookie, when `logout()` is called, then the first `fetch` call is `("/api/auth/csrf", …)` with method `GET` (or no method). If the stub sets `document.cookie = "XSRF-TOKEN=fresh1"` during that call, the second call is `("/api/auth/logout", …)` with `method` `"POST"` and `headers["X-XSRF-TOKEN"]` `"fresh1"`. | frontend |
| FR1-UI16 | API client: (a) when a request gets 401 with body `{"message":"Invalid student ID or password."}`, the promise rejects with an `ApiError` whose `status` is `401` and `message` is `Invalid student ID or password.`; (b) when the body is not JSON and the status is 502, the rejection has `status` `502` and `message` `Request failed with status 502`; (c) `fetchMe()` resolves `null` on a 401 response and resolves the profile on 200. | frontend |
| FR1-UI17 | Given `login()` rejects with `new ApiError(500, "An unexpected error occurred.")` (or with a non-`ApiError` such as `new TypeError("Failed to fetch")`), when valid-looking credentials are submitted, then `[data-testid="login-error"]` has text `Unable to sign in. Please try again later.` | frontend |

### NFR2: Only authenticated students can access registration functions
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| NFR2-AC1 | When an anonymous user sends `GET /api/dashboard`, then status is 401 with JSON error `Authentication required.`, and the response has no `Location` header (no redirect). | backend |
| NFR2-AC2 | When an anonymous user sends `GET` to each of `/api/auth/me`, `/api/courses`, `/api/registrations` and `/api/anything/else`, then each response is 401 with JSON error `Authentication required.` (security applies even to paths with no controller yet). | backend |
| NFR2-AC3 | When an anonymous user sends `POST /api/registrations` with a valid CSRF token and JSON body `{"courseId":1}`, then status is 401 with JSON error `Authentication required.`, and the row counts of tables `registration` and `audit_log` are unchanged. | backend |
| NFR2-AC4 | When `POST /api/auth/login` is sent with valid credentials but **without** a CSRF token, then status is 403 with JSON error `Invalid or missing CSRF token.`, and the result is `unauthenticated()`. | backend |
| NFR2-AC5 | Given a successful JSON login as `410000001`, the authenticated principal has exactly one authority, `ROLE_STUDENT` (`authenticated().withAuthorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))`). | backend |
| NFR2-AC6 | `StudentUserDetailsService.loadUserByUsername("410000001")` returns a `UserDetails` with username `410000001`, the stored BCrypt hash as password, and authority `ROLE_STUDENT`. `loadUserByUsername("499999999")` throws `UsernameNotFoundException`. | backend |
| NFR2-AC7 | When an anonymous user sends `GET /assets/missing.js` and `GET /favicon.svg`, then neither status is 401 or 3xx (static assets are public; 200 or 404 depending on whether the frontend build is on the classpath). | backend |

### SK: Skeleton, data model, seed data, Clock, build
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| SK-AC1 | The Spring application context loads. It contains exactly one `java.time.Clock` bean and a `PasswordEncoder` bean that is an instance of `BCryptPasswordEncoder`. | backend |
| SK-AC2 | The production `Clock` bean's zone is `ZoneId.of("Asia/Taipei")`. | backend |
| SK-AC3 | After startup, the seed row counts are: students 3, semesters 2, courses 4, registrations 1, registration periods 2, audit logs 0. | backend |
| SK-AC4 | Given course `CS101` in semester `115-1` loaded twice as two detached instances `a` and `b` (the test must NOT be `@Transactional`): change `a.availableSeats` and save it with `saveAndFlush`, then change `b.availableSeats` and save it with `saveAndFlush`. The second save throws `org.springframework.orm.ObjectOptimisticLockingFailureException`. | backend |
| SK-AC5 | Given the seed registration (student `410000003`, course `MA101`/`115-1`), when another `Registration` for the same student and course is saved with `saveAndFlush`, then `org.springframework.dao.DataIntegrityViolationException` is thrown. | backend |
| SK-AC6 | `StudentRepository.findByStudentId("410000002")` returns a student named `Bob Lin`. `CourseRepository.findByCourseCodeAndSemester_Code("CS102", "114-2")` returns a course with `creditHours` 3 and `availableSeats` 45. `SemesterRepository.findByCurrentTrue()` returns semester `115-1`. | backend |
| SK-AC7 | The context's `DataSource` connection URL (`dataSource.getConnection().getMetaData().getURL()`) matches the regex `^jdbc:h2:mem:tku-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`. | backend |
| SK-AC8 | Given the test's context, a second context is started with `new SpringApplicationBuilder(RegistrationApplication.class).properties("server.port=0").run()` and then closed. The second context's DataSource URL differs from the first, and afterwards the first context still reports 3 students (the second context's `create-drop` did not touch the first database). | backend |
| SK-AC9 | `ClassUtils.isPresent("org.thymeleaf.spring6.SpringTemplateEngine", null)` and `ClassUtils.isPresent("org.hibernate.validator.HibernateValidator", null)` are both `false` (the Thymeleaf and validation starters were removed). | backend |

Test-data rules:
- Tests that change seed data (SK-AC4, SK-AC5) must restore it, or use `@Transactional` rollback where that fits the test, or use `@DirtiesContext(classMode = AFTER_CLASS)`. Spring caches contexts with the same configuration, so those tests can still share a database.
- Do **not** add `src/test/resources/application.properties`, because it would replace the main one on the test classpath. To override a property, use `@SpringBootTest(properties = …)` or `@TestPropertySource`.
- To override the Clock, use `@MockitoBean Clock` (Spring Boot 3.4) or a `@TestConfiguration` with a `@Primary Clock`.
- The old Thymeleaf-based test classes (`LoginControllerTest`, `DashboardControllerTest`, `HtmlTestUtils`, and the redirect assertions in `SecurityAccessTest`) no longer apply. The tester replaces them. The coder must not touch them.

## 3. Contract

### 3.0 Project skeleton and build
**`pom.xml`** (coordinates unchanged: `edu.tku:course-registration:0.0.1-SNAPSHOT`, parent `spring-boot-starter-parent` 3.4.x, `java.version` 21):
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `com.h2database:h2` (runtime), `spring-boot-starter-test` (test), `spring-security-test` (test).
- **Remove**: `spring-boot-starter-thymeleaf`, `thymeleaf-extras-springsecurity6`, `spring-boot-starter-validation`.
- Property `<skipTests>false</skipTests>` so the npm test execution can reference `${skipTests}`.
- Plugin `com.github.eirslett:frontend-maven-plugin:1.15.1`, `<workingDirectory>frontend</workingDirectory>`, executions in this order:

| Execution id | Goal | Phase | Configuration |
|---|---|---|---|
| install-node-and-npm | `install-node-and-npm` | generate-resources | `<nodeVersion>v20.18.1</nodeVersion>` |
| npm-ci | `npm` | generate-resources | `<arguments>ci</arguments>` |
| npm-build | `npm` | generate-resources | `<arguments>run build</arguments>` |
| npm-test | `npm` | test | `<arguments>test</arguments>`, `<skip>${skipTests}</skip>` |

  The plugin's standard `-Dskip.npm -Dskip.installnodenpm` flags skip all frontend work, for example for fast single-class backend runs.
- `.gitignore` adds `frontend/node/`, `frontend/node_modules/`. `frontend/package-lock.json` is committed, because `npm ci` needs it.

**Main class**: `edu.tku.registration.RegistrationApplication` (unchanged).

**`src/main/resources/application.properties`**:
```properties
spring.application.name=course-registration
spring.datasource.url=jdbc:h2:mem:tku-${random.uuid};DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
spring.jpa.open-in-view=false
spring.h2.console.enabled=false
server.error.include-stacktrace=never
server.error.include-message=never
server.error.whitelabel.enabled=false
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
```
`${random.uuid}` is resolved once when the DataSource is bound, so each Spring context (including each cached test context) gets its own in-memory database.

**Delete**: `src/main/resources/templates/` (both templates) and `web/LoginController.java`. `web/DashboardController.java` is rewritten as a REST controller (§3.4).

Hibernate default naming is unchanged (camelCase field → snake_case column, `AuditLog` → table `audit_log`).

### 3.1 Domain / data model: UNCHANGED
The entities, the `AuditAction` enum, the constructors, the repositories and `data.sql` are exactly as built in revision 1. Do not modify them. For reference:

| Entity | Key fields |
|--------|-----------|
| Student | `id`, `studentId` (unique, 9), `name`, `email` (unique), `passwordHash` (BCrypt) |
| Semester | `id`, `code` (unique), `name`, `current` (`is_current`, getter `isCurrent()`) |
| Course | `id`, `courseCode`, `courseName`, `creditHours`, `instructor`, `capacity`, `availableSeats`, `semester`, `@Version Long version`; unique `(course_code, semester_id)` |
| Registration | `id`, `student`, `course`, `registrationDate`; unique `(student_id, course_id)` |
| AuditLog | `id`, `studentId` (String), `courseId` (Long), `action` (`AuditAction`), `timestamp` (`action_timestamp`) |
| RegistrationPeriod | `id`, `semester` (unique), `startAt`, `endAt` (inclusive, Asia/Taipei local) |

Repositories: `StudentRepository.findByStudentId`, `SemesterRepository.findByCode` / `findByCurrentTrue`, `CourseRepository.findByCourseCodeAndSemester_Code`, `RegistrationRepository`, `AuditLogRepository`, `RegistrationPeriodRepository.findBySemester_Code`.

Seed data (unchanged): students `410000001` Alice Chen, `410000002` Bob Lin and `410000003` Carol Wu, all with password `password123` stored as BCrypt. Semesters `115-1` "2026 Fall" (current) and `114-2` "2026 Spring". Courses CS101, CS201, MA101 (capacity 1, 0 seats) and CS102. One registration: 410000003 → MA101. Periods: `115-1` from 2026-08-01T00:00:00 to 2026-12-31T23:59:59, and `114-2` from 2026-01-15T00:00:00 to 2026-02-28T23:59:59. No audit logs. No new seed data is needed.

### 3.2 Service layer
Unchanged, reused as-is:
```java
// package edu.tku.registration.service
public record StudentProfile(String studentId, String name, String email) { }
public class StudentService { public StudentProfile getProfile(String studentId); }   // throws StudentNotFoundException
public class StudentNotFoundException extends RuntimeException { public StudentNotFoundException(String studentId); } // "Student not found: {id}"
```
New:
```java
// package edu.tku.registration.service
public record CurrentSemesterInfo(
        String code,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime registrationStartAt,  // null if no period
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime registrationEndAt,    // null if no period
        boolean registrationOpen) { }

@Service
public class SemesterService {
    public SemesterService(SemesterRepository semesterRepository,
                           RegistrationPeriodRepository registrationPeriodRepository,
                           Clock clock);
    /** Read-only transaction. Empty if no semester has current = true.
     *  Otherwise returns the current semester's code and name plus its RegistrationPeriod (looked up
     *  by findBySemester_Code). If no period exists, both dates are null and registrationOpen is false.
     *  registrationOpen = !now.isBefore(startAt) && !now.isAfter(endAt), where now = LocalDateTime.now(clock). */
    public Optional<CurrentSemesterInfo> getCurrentSemesterInfo();
}
```

### 3.3 Security (package `edu.tku.registration.security`)
`StudentUserDetailsService` is unchanged.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder();                       // new BCryptPasswordEncoder()
    @Bean AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder pe);
          // new ProviderManager(DaoAuthenticationProvider with uds + pe)
    @Bean SecurityContextRepository securityContextRepository();   // new HttpSessionSecurityContextRepository()
    @Bean CsrfTokenRepository csrfTokenRepository();               // CookieCsrfTokenRepository.withHttpOnlyFalse()
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ...);
}
```
Filter chain, exactly:
```java
http
  .authorizeHttpRequests(auth -> auth
      .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
      .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
      .requestMatchers("/api/**").hasRole("STUDENT")
      .anyRequest().permitAll())                     // SPA shell, static assets, /error
  .csrf(csrf -> csrf
      .csrfTokenRepository(csrfTokenRepository)
      .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
  .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
  .exceptionHandling(e -> e
      .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
      .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
  .logout(l -> l
      .logoutUrl("/api/auth/logout")                 // POST only (CSRF enabled)
      .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
      .invalidateHttpSession(true)
      .deleteCookies("JSESSIONID"))
  .formLogin(AbstractHttpConfigurer::disable)
  .httpBasic(AbstractHttpConfigurer::disable)
  .requestCache(rc -> rc.disable());
```
The session policy is Spring's default (`IF_REQUIRED`). A session is created only by a successful login. No remember-me.

Helper classes (same package):
```java
/** SPA CSRF handler (Spring Security reference "Single-Page Applications" recipe). */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();
    public void handle(HttpServletRequest req, HttpServletResponse res, Supplier<CsrfToken> token) {
        xor.handle(req, res, token);   // BREACH-protected value for request attributes / _csrf param
        token.get();                   // force the deferred token to load → XSRF-TOKEN cookie written on every response that lacks it
    }
    public String resolveCsrfTokenValue(HttpServletRequest req, CsrfToken token) {
        String header = req.getHeader(token.getHeaderName());     // "X-XSRF-TOKEN"
        return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(req, token);
    }
}
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint   // 401, {"message":"Authentication required."}
public class JsonAccessDeniedHandler implements AccessDeniedHandler
    // 403. If the exception is a org.springframework.security.web.csrf.CsrfException → {"message":"Invalid or missing CSRF token."}
    //      otherwise → {"message":"Access denied."}
```
Both write `Content-Type: application/json` and UTF-8, and never redirect.

CSRF protocol for the SPA:
- Cookie `XSRF-TOKEN`: readable by JavaScript (not HttpOnly), path `/`. It holds the **raw** token.
- The client sends the raw cookie value back in header `X-XSRF-TOKEN` on every non-GET/HEAD request.
- A response sets the cookie whenever the request had none (any path, because of `token.get()` in `handle`). `GET /api/auth/csrf` is the explicit bootstrap.
- After a successful login the token is cleared (FR1-EX12). After logout, Spring's `CsrfLogoutHandler` clears it. The client gets a fresh one on its next request.

### 3.4 Web layer (package `edu.tku.registration.web`)
DTOs (records): `LoginRequest(String studentId, String password)`, `ApiError(String message)`, `DashboardResponse(StudentProfile student, CurrentSemesterInfo currentSemester)`.

Controllers:
- `AuthController` (`@RestController @RequestMapping("/api/auth")`). Constructor: `(AuthenticationManager, SecurityContextRepository, CsrfTokenRepository, StudentService)`.
  - `login`: builds `UsernamePasswordAuthenticationToken.unauthenticated(studentId ?? "", password ?? "")` and calls `authenticationManager.authenticate(...)`. An `AuthenticationException` propagates to the advice. On success:
    1. If `request.getSession(false) != null`, call `request.changeSessionId()`.
    2. Create an empty context from `SecurityContextHolder.getContextHolderStrategy()`, set the authentication, set the context on the holder, and call `securityContextRepository.saveContext(ctx, request, response)`.
    3. Call `csrfTokenRepository.saveToken(null, request, response)`.
    4. Return `studentService.getProfile(auth.getName())`.
  - `me(Principal)` → `studentService.getProfile(principal.getName())`.
  - `csrf(CsrfToken token)` → calls `token.getToken()`, returns `ResponseEntity.noContent()`.
- `DashboardController` (`@RestController`, rewritten). `GET /api/dashboard` → `new DashboardResponse(studentService.getProfile(principal.getName()), semesterService.getCurrentSemesterInfo().orElse(null))`.
- `SpaForwardController` (`@Controller`):
  ```java
  @GetMapping({"/", "/{path:(?!api$|assets$)[^.]*}", "/{path:(?!api$|assets$)[^.]*}/**"})
  public String forward() { return "forward:/index.html"; }
  ```
  Any GET whose first segment is not `api` or `assets` and contains no `.` is forwarded to the SPA. `/api/**` and `/assets/**` are never forwarded. Everything else falls through to Spring Boot's static resource handler (`classpath:/static/`).
- `ApiExceptionHandler` (`@RestControllerAdvice`). Every response body is `ApiError`:

| Exception | Status | message |
|-----------|--------|---------|
| `org.springframework.security.core.AuthenticationException` | 401 | `Invalid student ID or password.` |
| `StudentNotFoundException` | 404 | `Student not found.` |
| `HttpMessageNotReadableException` | 400 | `Malformed request.` |
| `org.springframework.web.ErrorResponse` implementations (e.g. `NoResourceFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`) | `ex.getStatusCode()` | 404 → `Not found.`; 405 → `Method not allowed.`; 415 → `Unsupported media type.`; any other → `Request could not be processed.` |
| any other `Exception` | 500 | `An unexpected error occurred.` (logged server-side with the stack trace; never in the body) |

REST API:
| Method | Path | Request | Success (status + JSON) | Failures (status + JSON) | Auth |
|--------|------|---------|--------------------------|--------------------------|------|
| GET | /api/auth/csrf | — | 204, no body; sets `XSRF-TOKEN` cookie if absent | — | public |
| POST | /api/auth/login | `{"studentId": "...", "password": "..."}`, CSRF header | 200 `{"studentId","name","email"}`; session cookie; `XSRF-TOKEN` cleared | 401 `Invalid student ID or password.` · 400 `Malformed request.` · 403 `Invalid or missing CSRF token.` | public |
| POST | /api/auth/logout | no body, CSRF header | 204, no body; session invalidated; `XSRF-TOKEN` cleared | 403 `Invalid or missing CSRF token.` | handled by `LogoutFilter`; returns 204 even when anonymous |
| GET | /api/auth/me | — | 200 `{"studentId","name","email"}` | 401 `Authentication required.` · 404 `Student not found.` | ROLE_STUDENT |
| GET | /api/dashboard | — | 200 (shape below) | 401 `Authentication required.` | ROLE_STUDENT |
| any | /api/** (other) | — | — | 401 `Authentication required.` (anonymous) · 404 `Not found.` (authenticated, no handler) · 403 `Invalid or missing CSRF token.` (non-GET without a valid token, checked before authentication) | ROLE_STUDENT |
| GET | `/`, SPA routes (§ SpaForwardController) | — | 200, forward to `/index.html` | — | public |
| GET | `/index.html`, `/assets/**`, `/favicon.svg` | — | 200 static file (404 if missing) | — | public |

`GET /api/dashboard` example (Alice, Clock at 2026-09-01T09:00 Asia/Taipei):
```json
{
  "student": { "studentId": "410000001", "name": "Alice Chen", "email": "410000001@o365.tku.edu.tw" },
  "currentSemester": {
    "code": "115-1",
    "name": "2026 Fall",
    "registrationStartAt": "2026-08-01T00:00:00",
    "registrationEndAt": "2026-12-31T23:59:59",
    "registrationOpen": true
  }
}
```
`currentSemester` is JSON `null` when there is no current semester. Date-times are always `yyyy-MM-dd'T'HH:mm:ss`, local Asia/Taipei time, with no offset.

### 3.5 Frontend structure (`frontend/`)
- `package.json` scripts: `"dev": "vite"`, `"build": "tsc --noEmit && vite build"`, `"test": "vitest run"`.
- dependencies: `react` ^18.3.1, `react-dom` ^18.3.1, `react-router-dom` ^6.28.0, `antd` ^5.22.0, `@ant-design/icons` ^5.5.0.
- devDependencies: `typescript` ~5.6.0, `vite` ^5.4.0, `@vitejs/plugin-react` ^4.3.0, `vitest` ^2.1.0, `jsdom` ^25.0.0, `@testing-library/react` ^16.1.0, `@testing-library/dom` ^10.4.0, `@testing-library/jest-dom` ^6.6.0, `@testing-library/user-event` ^14.5.0, `@types/react` ^18.3.0, `@types/react-dom` ^18.3.0. MSW is not used.
- `vite.config.ts` (imports `defineConfig` from `vitest/config`):
  - `plugins: [react()]`
  - `server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } }`
  - `build: { outDir: '../target/classes/static', emptyOutDir: true }`
  - `test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test-setup.ts'], include: ['src/__tests__/**/*.test.{ts,tsx}'], css: false }`
- `tsconfig.json`: `strict: true`, `jsx: "react-jsx"`, `include: ["src"]`, `exclude: ["src/__tests__", "src/test-setup.ts"]`, so the production type check never depends on test files.
- `index.html`: `<title>Tamkang Course Registration System</title>` and a generic `public/favicon.svg` (a book/graduation-cap shape, no university marks).
- `src/test-setup.ts` is a **test file owned by the tester**. It must at least import `@testing-library/jest-dom/vitest` and stub `window.matchMedia` (antd `Layout.Sider` breakpoints and `Grid` need it). It may also stub `ResizeObserver` and `window.getComputedStyle`. Until the tester adds it, `npm test` fails, so the coder verifies with `npm run build`.

Files and exports (tests import these exact paths):
| File | Exports |
|------|---------|
| `src/main.tsx` | renders `<BrowserRouter><App/></BrowserRouter>` into `#root` |
| `src/App.tsx` | `export default function App()`, which renders antd `ConfigProvider` (theme) → antd `App` → `AuthProvider` → routes. **It contains no router**, so tests wrap it in `MemoryRouter`. |
| `src/api/types.ts` | `StudentProfile { studentId: string; name: string; email: string }`; `CurrentSemester { code: string; name: string; registrationStartAt: string \| null; registrationEndAt: string \| null; registrationOpen: boolean }`; `DashboardData { student: StudentProfile; currentSemester: CurrentSemester \| null }` |
| `src/api/client.ts` | `class ApiError extends Error { readonly status: number; constructor(status: number, message: string) }`; `function getCsrfToken(): string \| null`; `async function apiFetch<T>(path: string, options?: { method?: string; body?: unknown }): Promise<T>` |
| `src/api/auth.ts` | `login(studentId: string, password: string): Promise<StudentProfile>`; `logout(): Promise<void>`; `fetchMe(): Promise<StudentProfile \| null>` |
| `src/api/dashboard.ts` | `fetchDashboard(): Promise<DashboardData>` |
| `src/auth/AuthContext.tsx` | `AuthProvider`, `useAuth(): { status: 'loading' \| 'authenticated' \| 'anonymous'; user: StudentProfile \| null; login(studentId, password): Promise<void>; logout(): Promise<void>; markAnonymous(): void }` |
| `src/auth/ProtectedRoute.tsx` | `ProtectedRoute` (layout route element rendering `<Outlet/>`) |
| `src/components/AppLayout.tsx` | `AppLayout` (shell with `<Outlet/>`) |
| `src/pages/LoginPage.tsx` | `LoginPage` |
| `src/pages/DashboardPage.tsx` | `DashboardPage` |

**`apiFetch` behaviour** (exact, because FR1-UI14–16 depend on it):
- It always calls `fetch(path, init)` with `path` as given (a relative string such as `"/api/auth/login"`). `init` contains `method` (default `"GET"`), `credentials: "same-origin"`, and `headers`, which is a **plain object**: `{ "Accept": "application/json" }`, plus `"Content-Type": "application/json"` when `body` is given (then `init.body = JSON.stringify(body)`).
- For any method other than GET/HEAD: read `getCsrfToken()`. If it is `null`, first `await fetch("/api/auth/csrf", { method: "GET", credentials: "same-origin" })` and read it again. If a token exists, set `headers["X-XSRF-TOKEN"]` to it. If it is still null, send the request without the header.
- `getCsrfToken()` returns the `decodeURIComponent`-ed value of the `XSRF-TOKEN` cookie from `document.cookie`, or `null`.
- On `response.ok`: a 204 (or empty body) resolves `undefined`, otherwise `response.json()`. When not ok: try to parse JSON `{message}`, then throw `new ApiError(response.status, message)`. If parsing fails or there is no `message`, use `Request failed with status ${status}`. Network errors propagate unchanged.
- `fetchMe()` returns `null` when `ApiError.status === 401`, and rethrows anything else.

**Auth state rules:**
- On mount, `AuthProvider` sets `status='loading'` and calls `fetchMe()` once. A profile → `authenticated`. `null` or a rejection → `anonymous`.
- `login()` calls the API `login`. On success it sets `user` and `authenticated`. On failure it rethrows to the page.
- `logout()` calls the API `logout`, ignores any error, and always ends `anonymous` with `user=null`.
- `markAnonymous()` sets `anonymous` / `null`.

### 3.6 Frontend routes
| Route | Element | Auth | Behaviour |
|-------|---------|------|-----------|
| `/login` | `LoginPage` | public | If `status==='authenticated'` → `<Navigate to="/dashboard" replace/>`. If `loading` → `auth-loading`. On successful login → `navigate("/dashboard", { replace: true })`. |
| `/dashboard` | `ProtectedRoute` → `AppLayout` → `DashboardPage` | yes | Shows the dashboard. |
| `/` | `<Navigate to="/dashboard" replace/>` | — | Then the protected-route rule applies. |
| `*` | `<Navigate to="/dashboard" replace/>` | — | Same as `/`. |

**ProtectedRoute:**
- `loading` → `<div data-testid="auth-loading">` with a centred antd `Spin` (tip `Loading...`).
- `anonymous` → `<Navigate to="/login" replace/>`.
- `authenticated` → `<Outlet/>`.

**DashboardPage:** calls `fetchDashboard()` on mount.
- While pending → `<div data-testid="dashboard-loading">` with `Spin`.
- On rejection with an `ApiError` of status 401 → `markAnonymous()`, which leads to `/login`.
- On any other rejection → `dashboard-error` Alert.

**Logout:** clicking `logout-button` → `await auth.logout()` → `navigate("/login", { replace: true, state: { loggedOut: true } })`. `LoginPage` shows `logout-message` only when `location.state?.loggedOut === true`.

### 3.7 User-facing messages (exact text)
Backend (JSON `message`):
| Key | Text |
|-----|------|
| login.invalid | Invalid student ID or password. |
| auth.required | Authentication required. |
| csrf.invalid | Invalid or missing CSRF token. |
| access.denied | Access denied. |
| request.malformed | Malformed request. |
| student.notFound | Student not found. |
| notFound | Not found. |
| methodNotAllowed | Method not allowed. |
| unsupportedMediaType | Unsupported media type. |
| requestFailed | Request could not be processed. |
| server.error | An unexpected error occurred. |

Frontend:
| Key | Text |
|-----|------|
| app.name | Tamkang Course Registration System |
| login.title | Student Login |
| login.subtitle | Sign in with your student ID and password |
| login.studentId.label | Student ID |
| login.studentId.placeholder | e.g. 410000001 |
| login.password.label | Password |
| login.submit | Log in |
| login.studentId.required | Please enter your student ID. |
| login.password.required | Please enter your password. |
| login.error.invalid | Invalid student ID or password. (shown from the 401 `ApiError.message`; if a 401 carries no message, show this text) |
| login.error.generic | Unable to sign in. Please try again later. |
| login.loggedOut | You have been logged out. |
| shell.logout | Log out |
| nav.dashboard | Dashboard |
| auth.loading | Loading... |
| dashboard.welcome | Welcome, {name} |
| dashboard.subtitle | Here is an overview of your student account. |
| dashboard.profile.title | Student Profile |
| dashboard.profile.studentId / name / email | Student ID / Name / Email (Descriptions labels) |
| dashboard.semester.title | Current Semester |
| dashboard.semester.labels | Semester / Registration Period / Status |
| dashboard.semester.value | {name} ({code}) |
| dashboard.period.value | {start} to {end}, each formatted `YYYY-MM-DD HH:mm` (take the API string, replace `T` with a space, keep the first 16 characters) |
| dashboard.period.none | Not scheduled |
| dashboard.status.open | Open (green `Tag`) |
| dashboard.status.closed | Closed (default/red `Tag`) |
| dashboard.semester.empty | No current semester has been set. |
| dashboard.error | Unable to load your dashboard. Please try again later. |
| footer | Tamkang Course Registration System · Phase 1 |

Any 401 from login shows its `ApiError.message`. Every other login failure, whether a non-401 `ApiError` or a non-`ApiError` error, shows `login.error.generic`.

### 3.8 UI elements tests rely on
Visual direction (not asserted, but required): a navy primary colour (antd `ConfigProvider` token `colorPrimary: "#1f3a68"`), a light grey content background and white cards.
- Login: full-height page with a subtle gradient or university-blue background and a centred brand block (a generic antd icon such as `ReadOutlined` in a circle, plus the system name) above a `Card` (max-width ~400px) holding the form.
- Shell: antd `Layout` with a dark `Header` (brand at left, user area at right), a `Sider` (`breakpoint="lg"`, collapsible) holding a `Menu`, `Content` with padding, and a `Footer`.
- Dashboard: the page heading, then cards in an antd `Row`/`Col` grid (profile and semester side by side on wide screens).

`data-testid` attributes. If an antd component does not forward `data-testid`, put the attribute on a wrapping element. Inputs and buttons must carry it on the real `<input>` / `<button>`.
| data-testid | Element | Content / notes |
|---|---|---|
| login-brand | brand title on the login page | `Tamkang Course Registration System` |
| login-card | the login `Card` | title `Student Login` |
| login-student-id | `<input>` of the Student ID field (antd `Form.Item` label `Student ID`, `name="studentId"`) | rule `required` + `whitespace: true` |
| login-password | `<input type="password">` (`Input.Password`, `Form.Item` label `Password`, `name="password"`) | rule `required` |
| login-submit | `<button type="submit">` | text `Log in`; `loading` while submitting |
| login-error | error `Alert` (`type="error"`) | rendered only after a failed submit, removed on the next submit |
| logout-message | success `Alert` | `You have been logged out.`, only when `state.loggedOut === true` |
| auth-loading | loading wrapper (ProtectedRoute and LoginPage while `status==='loading'`) | |
| app-title | system name in the header | `Tamkang Course Registration System` |
| user-menu | header user area | contains the `Avatar` (initial of the name), `user-name` and `logout-button` |
| user-name | `<span>` | exactly the user's name |
| logout-button | `<button>` | text `Log out` (with `LogoutOutlined` icon) |
| side-nav | wrapper of the sidebar `Menu` | exactly one item, `Dashboard` (key `/dashboard`, `DashboardOutlined`), selected |
| welcome-message | `<h2>` (antd `Typography.Title level={2}`) | exactly `Welcome, {name}` |
| dashboard-loading | loading wrapper on the dashboard | |
| dashboard-error | error `Alert` | `Unable to load your dashboard. Please try again later.` |
| profile-card | profile `Card` | title `Student Profile` |
| profile-student-id / profile-name / profile-email | `<span>` inside the `Descriptions` items | exact values |
| semester-card | semester `Card` | title `Current Semester` |
| semester-name | `<span>` | `{name} ({code})` |
| registration-period | `<span>` | formatted period or `Not scheduled` |
| registration-status | antd `Tag` | `Open` / `Closed` |
| semester-empty | `<span>` / antd `Empty` description wrapper | `No current semester has been set.` |

Test harness assumptions the coder must keep true:
- `render(<MemoryRouter initialEntries={[...]}><App/></MemoryRouter>)` works with no other providers.
- `vi.mock("../api/auth")` and `vi.mock("../api/dashboard")` (paths relative to `src/__tests__/`) replace every network access made by the components. Components never call `fetch` directly.

## 4. Assumptions & decisions
Items 1–2 and 9–15 are carried over from revision 1. The rest are new or changed.
1. **Student ID format:** a 9-digit numeric string, matched exactly and case-sensitively, with no trimming on the server. The frontend's whitespace-only check is only a form validation.
2. **Surrogate keys:** unchanged (`Long id`; `Student.studentId` is a separate unique column; `AuditLog` uses plain columns).
3. **(changed)** A **single error message** (`Invalid student ID or password.`, 401) covers a wrong ID, a wrong password, and blank or missing fields. There is no Bean Validation and no 400 for blanks, so the validation starter is removed. Only unparseable JSON gives 400 `Malformed request.`
4. After login the SPA always goes to `/dashboard`, even if the user first requested another route.
5. **(changed) The dashboard shows the profile card and a read-only current-semester card** (semester name/code, registration period, Open/Closed). The data comes from existing seed tables and the injected Clock (inclusive bounds). This only *displays* the period. Enforcing it (disabling registration) remains FR7, which should reuse `SemesterService`. Registered courses and credits come with FR5.
6. **(changed) Logout** is `POST /api/auth/logout`, CSRF-protected, returning 204. Spring's `LogoutFilter` handles it before authorization, so it is idempotent and returns 204 even for anonymous callers. This is the one `/api/**` path besides login/csrf that is not role-gated.
7. **(changed)** An authenticated user who opens `/login` is redirected to `/dashboard`.
8. **(changed) NFR2 for the API:** every `/api/**` endpoint needs `ROLE_STUDENT` except `POST /api/auth/login` and `GET /api/auth/csrf`. Unauthenticated API calls get 401 JSON, never a redirect. Non-API paths (the SPA shell and static assets) are public because they hold no student data. The client-side route guard is for UX only.
9. `Semester.current` gives the default "selected semester" (seed `115-1`).
10. Registration periods are inclusive `LocalDateTime` bounds in Asia/Taipei time. FR7 tests must use a fixed Clock.
11. The Clock zone is `Asia/Taipei`.
12. The seed full course `MA101` (capacity 1, 0 seats) is ready for FR6.
13. Drop semantics are left to FR4 (the unique constraint assumes a drop deletes the row).
14. Seed passwords: `password123` as BCrypt cost 10. The plain text appears only in this spec and in tests.
15. No account lockout, password reset, sign-up or remember-me.
16. **(new) CSRF:** cookie-based (`XSRF-TOKEN`, not HttpOnly) with the raw value echoed in `X-XSRF-TOKEN`, using the Spring Security SPA handler recipe (§3.3). Login itself needs a CSRF token, so the SPA bootstraps one via `GET /api/auth/csrf` when the cookie is missing. The token is cleared at login and logout, and the client fetches a new one lazily.
17. **(new) Sessions:** a server-side HTTP session (`JSESSIONID`, HttpOnly, SameSite=Lax), with the id changed at login and Spring Boot's default 30-minute timeout. No JWT. When the session expires, the next API call returns 401 and the SPA returns to `/login`. The request cache is disabled.
18. **(new) Login is a REST controller** (`AuthenticationManager` + `HttpSessionSecurityContextRepository`), not `formLogin`, because the contract is JSON in and JSON out.
19. **(new) Navigation shows only "Dashboard".** There are no disabled "coming soon" items, so no dead UI and no tests to rewrite later. FR2–FR5 each add their own menu item.
20. **(new) The user menu is an always-visible header area** (avatar, name, **Log out** button) rather than a dropdown. It is easier to use and testable without portal or animation quirks in jsdom.
21. **(new) Unknown frontend routes redirect to `/dashboard`** (and from there to `/login` if anonymous). There is no 404 page in Phase 1.
22. **(new) Branding:** the text "Tamkang Course Registration System" with a generic antd icon and a navy colour scheme. No real logos, seals or trademarks. The UI is English only.
23. **(new) The "logged out" notice** travels in router state (`{ loggedOut: true }`), not a query parameter, so it does not reappear on refresh.
24. **(new) `/api/dashboard`** is the dashboard's data endpoint (profile + semester). `/api/auth/me` returns the same profile shape and serves the auth bootstrap. There is no separate `/api/students/me`.
25. **(new) Date display** is `YYYY-MM-DD HH:mm` via string slicing. No date library, and no timezone conversion because the API sends Taipei local time.
26. **(new) Test DB isolation:** the main `application.properties` uses `jdbc:h2:mem:tku-${random.uuid}`, so each Spring context gets its own database. No test-side `application.properties`.
27. **(new) Frontend tests mock the API modules** (`vi.mock`) or stub `fetch`. No MSW, so there are fewer dependencies and fewer jsdom/Node fetch polyfill issues.
28. **(new) Frontend versions** are pinned to React 18 / antd 5 / Vite 5 / Vitest 2 / Node 20.18.1 (a mutually compatible set) instead of the newest majors.
29. **(new) `server.error.whitelabel.enabled=false`.** Errors are JSON from the advice or Boot's `/error` JSON. There is no HTML error page, because the SPA owns all HTML.
30. **(new) Size / split:** this revision is large (about 26 backend and about 18 frontend criteria plus build wiring). The recommended build is two `/build-feature` passes against this one spec. Slice A, backend (§3.0–3.4, all *backend* criteria): the coder may skip npm with `-Dskip.npm -Dskip.installnodenpm` until Slice B exists. Slice B, frontend (§3.5–3.8, all *frontend* criteria, plus enabling the frontend-maven-plugin executions).

## 5. Out of scope
- Course browsing/search (FR2), registering (FR3/FR6), dropping (FR4), registered-course list / credits / print (FR5), and registration-period *enforcement* (FR7). There are no screens, menu items or endpoints for these.
- Services that write `AuditLog` rows (FR3/FR4).
- Account management: sign-up, password change/reset, lockout, remember-me, admin users, JWT/OAuth.
- i18n (Chinese UI), dark mode, mobile-specific layouts beyond antd's responsive defaults, and end-to-end browser tests (Playwright/Cypress).
- NFR1 performance, NFR3 availability, NFR5 click counts.
- Real university branding assets.
