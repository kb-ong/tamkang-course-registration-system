export interface StudentProfile {
  studentId: string;
  name: string;
  email: string;
}

export interface CurrentSemester {
  code: string;
  name: string;
  registrationStartAt: string | null;
  registrationEndAt: string | null;
  registrationOpen: boolean;
}

export interface DashboardData {
  student: StudentProfile;
  currentSemester: CurrentSemester | null;
}

export interface SemesterOption {
  code: string;
  name: string;
  current: boolean;
}

export interface CourseSummary {
  id: number;
  code: string;
  name: string;
  credits: number;
  instructor: string;
  capacity: number;
  availableSeats: number;
  registered: boolean;
}

export interface CourseListing {
  semester: SemesterOption | null;
  courses: CourseSummary[];
}

export interface RegistrationResult {
  message: string;
  registrationId: number;
  courseId: number;
  courseCode: string;
  availableSeats: number;
}
