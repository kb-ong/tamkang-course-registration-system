// FR7 frontend criteria on /courses: FR7-UI1 .. FR7-UI8.
import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/auth", () => ({ login: vi.fn(), logout: vi.fn(), fetchMe: vi.fn() }));
vi.mock("../api/dashboard", () => ({ fetchDashboard: vi.fn() }));
vi.mock("../api/courses", () => ({
  fetchSemesters: vi.fn(),
  fetchCourses: vi.fn(),
  registerForCourse: vi.fn(),
}));
vi.mock("../api/registrations", () => ({ fetchRegistrations: vi.fn(), dropCourse: vi.fn() }));

import { fetchMe } from "../api/auth";
import { fetchDashboard } from "../api/dashboard";
import { fetchCourses, fetchSemesters, registerForCourse } from "../api/courses";
import { fetchRegistrations } from "../api/registrations";
import { ApiError } from "../api/client";
import type { CourseListing, RegistrationWindow } from "../api/types";
import { ALICE, dashboardExample, renderApp } from "./fixtures";
import { SEMESTERS, listingFixture, springListing } from "./coursesFixtures";
import { CLOSED_PERIOD, NO_PERIOD, OPEN_PERIOD, emptySchedule } from "./scheduleFixtures";

const mockCourses = vi.mocked(fetchCourses);
const mockRegister = vi.mocked(registerForCourse);

beforeEach(() => {
  vi.mocked(fetchMe).mockReset().mockResolvedValue({ ...ALICE });
  vi.mocked(fetchDashboard).mockReset().mockResolvedValue(dashboardExample());
  vi.mocked(fetchSemesters).mockReset().mockResolvedValue(SEMESTERS.map((s) => ({ ...s })));
  vi.mocked(fetchRegistrations).mockReset().mockResolvedValue(emptySchedule());
  mockCourses.mockReset().mockResolvedValue(listing(OPEN_PERIOD));
  mockRegister.mockReset();
});

function listing(period: RegistrationWindow | null | undefined, registeredMa101 = false): CourseListing {
  const l = listingFixture();
  l.courses[2].registered = registeredMa101;
  if (period !== undefined) l.registrationPeriod = period;
  return l;
}

async function renderCourses(l?: CourseListing) {
  if (l) mockCourses.mockReset().mockResolvedValue(l);
  const result = renderApp("/courses");
  await screen.findByTestId("courses-title");
  await screen.findByTestId("course-row-1");
  return result;
}

describe("FR7: period banner and disabled Register", () => {
  it("FR7-UI1: open period shows the open banner, enabled Register and no closed note", async () => {
    await renderCourses(listing(OPEN_PERIOD));
    const banner = await screen.findByTestId("period-banner");
    expect(banner).toHaveAttribute("data-status", "open");
    expect(banner.textContent?.trim()).toBe("Registration is open. Period: 2026-08-01 00:00 to 2026-12-31 23:59.");
    expect(screen.getByTestId("register-btn-1")).toBeEnabled();
    expect(screen.queryByTestId("closed-note-1")).not.toBeInTheDocument();
  });

  it("FR7-UI2: closed period shows the closed banner, disabled Register, closed notes and no register call", async () => {
    const { user } = await renderCourses(listing(CLOSED_PERIOD));
    const banner = await screen.findByTestId("period-banner");
    expect(banner).toHaveAttribute("data-status", "closed");
    expect(banner.textContent?.trim()).toBe("Registration is closed. Period: 2026-08-01 00:00 to 2026-12-31 23:59.");
    for (const id of [1, 2]) {
      const btn = screen.getByTestId(`register-btn-${id}`);
      expect(btn).toBeDisabled();
      expect(btn).toHaveTextContent("Register");
      expect(screen.getByTestId(`closed-note-${id}`).textContent).toBe("Registration is closed.");
    }
    await user.click(screen.getByTestId("register-btn-1"));
    expect(mockRegister).not.toHaveBeenCalled();
    expect(screen.getByTestId("course-row-1")).toBeInTheDocument();
    expect(screen.getByTestId("course-row-2")).toBeInTheDocument();
  });

  it("FR7-UI3: no period scheduled shows the not-scheduled banner and disabled Register", async () => {
    await renderCourses(listing(NO_PERIOD));
    const banner = await screen.findByTestId("period-banner");
    expect(banner).toHaveAttribute("data-status", "closed");
    expect(banner.textContent?.trim()).toBe(
      "Registration is closed. No registration period is scheduled for this semester.",
    );
    expect(screen.getByTestId("register-btn-1")).toBeDisabled();
    expect(screen.getByTestId("register-btn-2")).toBeDisabled();
  });

  it("FR7-UI4: closed period and a registered course shows the Registered tag, disabled button and no closed note", async () => {
    await renderCourses(listing(CLOSED_PERIOD, true));
    expect(screen.getByTestId("registered-tag-3")).toHaveTextContent("Registered");
    expect(screen.getByTestId("register-btn-3")).toBeDisabled();
    expect(screen.queryByTestId("closed-note-3")).not.toBeInTheDocument();
  });

  it("FR7-UI5: switching semester updates the banner and disables CS102's Register", async () => {
    mockCourses.mockReset().mockImplementation(async (params) => {
      if (params?.semester === "114-2") {
        const s = springListing();
        s.registrationPeriod = { startAt: "2026-01-15T00:00:00", endAt: "2026-02-28T23:59:59", open: false };
        return s;
      }
      return listing(OPEN_PERIOD);
    });
    const { user } = await renderCourses();
    expect(await screen.findByTestId("period-banner")).toHaveAttribute("data-status", "open");

    await user.selectOptions(screen.getByTestId("semester-select"), "114-2");

    await screen.findByTestId("course-row-4");
    await waitFor(() => expect(screen.getByTestId("period-banner")).toHaveAttribute("data-status", "closed"));
    expect(screen.getByTestId("period-banner").textContent?.trim()).toBe(
      "Registration is closed. Period: 2026-01-15 00:00 to 2026-02-28 23:59.",
    );
    expect(screen.getByTestId("register-btn-4")).toBeDisabled();
  });

  it("FR7-UI6: a listing without registrationPeriod has no banner and Register stays enabled", async () => {
    await renderCourses(listing(undefined));
    expect(screen.getByTestId("register-btn-1")).toBeEnabled();
    expect(screen.queryByTestId("period-banner")).not.toBeInTheDocument();
    expect(screen.queryByTestId("closed-note-1")).not.toBeInTheDocument();
  });

  it("FR7-UI6: registrationPeriod null with semester null has no banner", async () => {
    const l: CourseListing = { semester: null, registrationPeriod: null, courses: [] };
    mockCourses.mockReset().mockResolvedValue(l);
    renderApp("/courses");
    await screen.findByTestId("courses-title");
    await screen.findByTestId("courses-empty");
    expect(screen.queryByTestId("period-banner")).not.toBeInTheDocument();
  });

  it("FR7-UI7: a server closed rejection shows the message, refetches and flips the banner to closed", async () => {
    mockCourses
      .mockReset()
      .mockResolvedValueOnce(listing(OPEN_PERIOD))
      .mockResolvedValue(listing(CLOSED_PERIOD));
    mockRegister.mockRejectedValue(new ApiError(409, "Registration is closed for semester 115-1."));
    const { user } = await renderCourses();
    expect(await screen.findByTestId("period-banner")).toHaveAttribute("data-status", "open");

    await user.click(screen.getByTestId("register-btn-1"));

    await waitFor(() => expect(screen.getByTestId("period-banner")).toHaveAttribute("data-status", "closed"));
    expect(screen.getByTestId("register-error")).toHaveTextContent("Registration is closed for semester 115-1.");
    expect(screen.getByTestId("register-btn-1")).toBeDisabled();
    expect(mockCourses.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it("FR7-UI8: the banner is shown while the course table is empty", async () => {
    const l = listing(CLOSED_PERIOD);
    l.courses = [];
    mockCourses.mockReset().mockResolvedValue(l);
    renderApp("/courses");
    await screen.findByTestId("courses-title");
    expect(await screen.findByTestId("courses-empty")).toHaveTextContent("No courses found.");
    expect(screen.getByTestId("period-banner")).toHaveAttribute("data-status", "closed");
  });
});
