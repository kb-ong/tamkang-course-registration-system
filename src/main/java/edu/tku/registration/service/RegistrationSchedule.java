package edu.tku.registration.service;

import java.util.List;

public record RegistrationSchedule(SemesterOption semester,
        List<SemesterOption> semesters,
        RegistrationPeriodInfo registrationPeriod,
        List<RegisteredCourse> courses,
        int totalCredits) { }
