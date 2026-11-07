-- Seed data for Phase 1. No explicit ids; foreign keys resolved by sub-select.
-- Student passwords are BCrypt (cost 10) hashes only.

INSERT INTO student (student_id, name, email, password_hash) VALUES
    ('410000001', 'Alice Chen', '410000001@o365.tku.edu.tw', '$2a$10$Z79X9SDnp/na/LsfxBs0Aup9U61Nu4hd4hfGOCapQnzLHITYzMDSK'),
    ('410000002', 'Bob Lin',    '410000002@o365.tku.edu.tw', '$2a$10$M8KG01CJeVTJEHEvADaZdO35BfUN37TT2o4VFXukBeQinAatK8KXy'),
    ('410000003', 'Carol Wu',   '410000003@o365.tku.edu.tw', '$2a$10$QByLjqd9HqBmHSb1i24R8.JfpPP4uQjyaBgZwtYaif126cGdXtpwm');

INSERT INTO semester (code, name, is_current) VALUES
    ('115-1', '2026 Fall',   TRUE),
    ('114-2', '2026 Spring', FALSE);

INSERT INTO course (course_code, course_name, credit_hours, instructor, capacity, available_seats, semester_id, version) VALUES
    ('CS101', 'Introduction to Programming', 3, 'Prof. Lee',   50, 50, (SELECT id FROM semester WHERE code = '115-1'), 0),
    ('CS201', 'Data Structures',             3, 'Prof. Chang', 40, 40, (SELECT id FROM semester WHERE code = '115-1'), 0),
    ('MA101', 'Calculus I',                  4, 'Prof. Huang',  1,  0, (SELECT id FROM semester WHERE code = '115-1'), 0),
    ('CS102', 'Object-Oriented Programming', 3, 'Prof. Lee',   45, 45, (SELECT id FROM semester WHERE code = '114-2'), 0);

INSERT INTO registration (student_id, course_id, registration_date) VALUES
    ((SELECT id FROM student WHERE student_id = '410000003'),
     (SELECT c.id FROM course c JOIN semester s ON c.semester_id = s.id WHERE c.course_code = 'MA101' AND s.code = '115-1'),
     TIMESTAMP '2026-08-10 10:00:00');

-- WARNING: the 115-1 period below ends 2026-12-31 23:59:59. After that date the app reports
-- registration as closed (register and drop are refused). To keep working, edit the end_at of
-- the 115-1 row below (the value TIMESTAMP '2026-12-31 23:59:59').
INSERT INTO registration_period (semester_id, start_at, end_at) VALUES
    ((SELECT id FROM semester WHERE code = '115-1'), TIMESTAMP '2026-08-01 00:00:00', TIMESTAMP '2026-12-31 23:59:59'),
    ((SELECT id FROM semester WHERE code = '114-2'), TIMESTAMP '2026-01-15 00:00:00', TIMESTAMP '2026-02-28 23:59:59');
