package edu.tku.registration.service;

import java.util.List;

public record CourseListing(SemesterOption semester, List<CourseSummary> courses) { }
