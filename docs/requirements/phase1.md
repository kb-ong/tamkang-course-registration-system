# Course Registration System — Phase 1 Requirements

## Goal
Allow students to browse available courses and register for courses during a registration period.

## Functional Requirements

### FR1. Student Login
Students must be able to log in using their university student ID and password.
- AC1: Given a valid student ID and password, when the student logs in, then access is granted.
- AC2: Given an invalid student ID or password, when the student attempts to log in, then an error message is displayed.
- AC3: Logged-in students can view their personal dashboard.

### FR2. View Available Courses
Students must be able to view courses offered for the selected semester.
- AC1: Students can search courses by course code or name.
- AC2: Students can view course details: course code, course name, credit hours, instructor, available seats.
- AC3: Only courses offered in the selected semester are displayed.

### FR3. Register for Course
Students must be able to register for available courses.
- AC1: Student can select a course and submit registration.
- AC2: Course is added to the student's schedule.
- AC3: Available seat count decreases by 1.
- AC4: Student receives a confirmation message.
- AC5: System prevents duplicate registration of the same course.

### FR4. Drop Course
Students must be able to drop a previously registered course before the registration deadline.
- AC1: Student can view registered courses.
- AC2: Student can remove a registered course.
- AC3: Available seat count increases by 1.
- AC4: Course is removed from the student's schedule.
- AC5: Confirmation message is displayed.

### FR5. View Registered Courses
Students must be able to view all registered courses.
- AC1: Student dashboard displays registered courses.
- AC2: Total credit hours are shown.
- AC3: Student can print or download the course list.

### FR6. Course Capacity Validation
The system must prevent registration when course capacity is full.
- AC1: Registration is blocked when available seats = 0.
- AC2: Student receives a "Course Full" message.
- AC3: No registration record is created.

### FR7. Registration Period Control
Registration is only allowed during the approved registration period.
- AC1: Students can register only between configured start and end dates.
- AC2: Registration button is disabled outside the registration period.
- AC3: Existing registrations can still be viewed after the registration period ends.

## Non-Functional Requirements
| ID | Requirement | Acceptance Criteria |
|----|-------------|---------------------|
| NFR1 | Performance | Course search returns results within 3 seconds |
| NFR2 | Security | Only authenticated students can access registration functions |
| NFR3 | Availability | System available 99% during registration period |
| NFR4 | Audit | All registrations and drops are logged |
| NFR5 | Usability | Student can register for a course in fewer than 5 clicks |

## User Stories
- US1: As a student, I want to view available courses so that I can choose courses for the semester.
- US2: As a student, I want to register for a course so that I can attend it.
- US3: As a student, I want to drop a course so that I can change my study plan.
- US4: As a student, I want to see my registered courses so that I can verify my schedule.

## Core Data Model
- **Student:** StudentID, Name, Email
- **Course:** CourseID, CourseCode, CourseName, CreditHours, Capacity, AvailableSeats
- **Registration:** RegistrationID, StudentID, CourseID, RegistrationDate

## Suggested build order (one `/build-feature` run each)
1. **FR1** Login + project skeleton + data model + NFR2 security
2. **FR2** View/search courses (+ NFR1)
3. **FR3 + FR6** Register with capacity check (+ NFR4 audit, NFR5 clicks)
4. **FR4** Drop course (+ NFR4 audit)
5. **FR5** Registered courses dashboard, total credits, print/download
6. **FR7** Registration period control

NFR3 (99% availability) is a deployment/ops concern and is out of scope for code in Phase 1.
