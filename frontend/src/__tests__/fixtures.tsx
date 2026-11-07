// Shared fixtures and render helper for FR1 frontend tests (not a test file itself).
import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import App from "../App";
import type { DashboardData, StudentProfile } from "../api/types";

export const ALICE: StudentProfile = {
  studentId: "410000001",
  name: "Alice Chen",
  email: "410000001@o365.tku.edu.tw",
};

/** The §3.4 GET /api/dashboard example. Returns a fresh copy each call. */
export function dashboardExample(): DashboardData {
  return {
    student: { ...ALICE },
    currentSemester: {
      code: "115-1",
      name: "2026 Fall",
      registrationStartAt: "2026-08-01T00:00:00",
      registrationEndAt: "2026-12-31T23:59:59",
      registrationOpen: true,
    },
  };
}

/** Renders <App/> inside a MemoryRouter at the given entry (spec §3.8 harness). */
export function renderApp(path: string) {
  const user = userEvent.setup();
  const utils = render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  return { user, ...utils };
}
