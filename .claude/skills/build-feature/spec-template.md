# Spec: <feature-id> — <title>

Requirements covered: <FR/NFR ids>
Status: Draft (approved at the /build-feature human gate)

## 1. Summary
<2–4 sentences: what the student can do after this feature ships.>

## 2. Acceptance criteria
| ID | Criterion (Given / When / Then) | Test level |
|----|---------------------------------|------------|
| FRx-AC1 | Given …, when …, then … | service / mvc |
| FRx-EX1 | (extra edge case added by planner) | |

## 3. Contract
The coder and the tester both build against this section. Be exact.

### 3.1 Domain / data model
| Entity | Field | Type | Constraints |
|--------|-------|------|-------------|

Seed data (`data.sql`) needed by this feature:
- <e.g. student `S1001` / password `password123` (stored as BCrypt hash), course `CS101` with 0 seats>

### 3.2 Service layer
```java
// package edu.tku.registration.service
public class XxxService {
    /** <behaviour>; throws XxxException when … */
    ReturnType methodName(ParamType param);
}
```
Exceptions: `edu.tku.registration.service.<Name>Exception` → when it is thrown.

### 3.3 Web layer
REST API (all under `/api`, JSON in/out, CSRF token required on non-GET):
| Method | Path | Request | Success (status + JSON) | Failures (status + JSON) |
|--------|------|---------|--------------------------|--------------------------|
| GET | /api/courses | query `q`, `semester` | 200 `[{ "id", "code", ... }]` | 401 if not logged in |
| POST | /api/registrations | `{ "courseId": 1 }` | 201 `{ "message": "..." }` | 409 `{ "message": "Course Full" }` |

Frontend routes:
| Route | Page component | Auth | What it shows / does |
|-------|----------------|------|----------------------|

Authentication required: <yes/no per endpoint>

### 3.4 User-facing messages (exact text)
| Key | Text |
|-----|------|
| register.success | Successfully registered for {courseCode}. |

### 3.5 UI elements tests rely on
<e.g. the register button has `data-testid="register-btn-{courseId}"`, label "Register", and is disabled outside the period>

## 4. Assumptions & decisions
<Every gap in the requirements and the decision taken. The human confirms or overrules these at the approval gate.>
1. …

## 5. Out of scope
- …
