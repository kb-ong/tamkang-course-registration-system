package edu.tku.registration.service;

import java.util.List;

public record CourseListing(SemesterOption semester, RegistrationPeriodInfo registrationPeriod,
                            List<CourseSummary> courses) { }
