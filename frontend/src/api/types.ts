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
