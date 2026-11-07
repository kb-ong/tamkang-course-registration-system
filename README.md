# Tamkang Course Registration System

Phase 1 student course registration web app. It has a Spring Boot backend, a React frontend and an H2 in-memory database that is re-seeded on every start.

## Important: registration period

**The seeded `115-1` registration period ends on 2026-12-31 23:59:59. After that date the app reports registration as closed (register and drop are refused).** To keep working, edit the `115-1` row of the `INSERT INTO registration_period` statement at the end of `src/main/resources/data.sql` (the `2026-12-31 23:59:59` value).

## Run

```
mvn spring-boot:run
```

Then open http://localhost:8080.

The Maven build (`frontend-maven-plugin` in `pom.xml`) installs Node v20.18.1 locally, runs `npm ci` and `npm run build` in `frontend/`, and writes the built frontend into `target/classes/static`, which Spring Boot serves. The first run therefore needs network access to download Node and the npm packages. For frontend development you can instead run `npm run dev` in `frontend/` (Vite dev server on port 5173, which proxies `/api` to http://localhost:8080, so the backend must also be running).

## Seed logins

| Student ID | Name | Password |
|------------|------|----------|
| `410000001` | Alice Chen | `password123` |
| `410000002` | Bob Lin | `password123` |
| `410000003` | Carol Wu | `password123` |

## Tests

Backend (this also runs the frontend tests through Maven):

```
mvn -q test
```

Frontend only, from `frontend/`:

```
npm test
```
