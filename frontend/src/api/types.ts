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

export interface RegistrationWindow {
  startAt: string | null;
  endAt: string | null;
  open: boolean;
}

export interface CourseListing {
  semester: SemesterOption | null;
  registrationPeriod?: RegistrationWindow | null;
  courses: CourseSummary[];
}

export interface RegistrationResult {
  message: string;
  registrationId: number;
  courseId: number;
  courseCode: string;
  availableSeats: number;
}

export interface RegisteredCourse {
  registrationId: number;
  courseId: number;
  code: string;
  name: string;
  credits: number;
  instructor: string;
  semesterCode: string;
  registeredAt: string;
}

export interface RegistrationSchedule {
  semester: SemesterOption | null;
  semesters: SemesterOption[];
  registrationPeriod: RegistrationWindow | null;
  courses: RegisteredCourse[];
  totalCredits: number;
}

export interface DropResult {
  message: string;
  courseId: number;
  courseCode: string;
  availableSeats: number;
}
