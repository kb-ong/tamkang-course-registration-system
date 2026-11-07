package edu.tku.registration.web;

import edu.tku.registration.service.CurrentSemesterInfo;
import edu.tku.registration.service.StudentProfile;

public record DashboardResponse(StudentProfile student, CurrentSemesterInfo currentSemester) {
}
