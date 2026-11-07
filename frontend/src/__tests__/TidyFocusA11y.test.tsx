// TIDY-6a..6f: focus management and accessibility on the dashboard and /courses.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/auth", () => ({ login: vi.fn(), logout: vi.fn(), fetchMe: vi.fn() }));
vi.mock("../api/dashboard", () => ({ fetchDashboard: vi.fn() }));
vi.mock("../api/courses", () => ({ fetchSemesters: vi.fn(), fetchCourses: vi.fn(), registerForCourse: vi.fn() }));
vi.mock("../api/registrations", () => ({ fetchRegistrations: vi.fn(), dropCourse: vi.fn() }));
vi.mock("../utils/download", () => ({ downloadTextFile: vi.fn() }));

import { fetchMe } from "../api/auth";
import { fetchDashboard } from "../api/dashboard";
import { fetchCourses, fetchSemesters } from "../api/courses";
import { dropCourse, fetchRegistrations } from "../api/registrations";
import { ApiError } from "../api/client";
import type { CourseListing, RegistrationSchedule } from "../api/types";
import { ALICE, dashboardExample, renderApp } from "./fixtures";
import { SEMESTERS, listingFixture } from "./coursesFixtures";
import { CLOSED_PERIOD, DROP_RESULT, OPEN_PERIOD, onlyCs201, scheduleFixture } from "./scheduleFixtures";

const mockFetchReg = vi.mocked(fetchRegistrations);
const mockDrop = vi.mocked(dropCourse);
const mockCourses = vi.mocked(fetchCourses);

beforeEach(() => {
  vi.mocked(fetchMe).mockReset().mockResolvedValue({ ...ALICE });
  vi.mocked(fetchDashboard).mockReset().mockResolvedValue(dashboardExample());
  vi.mocked(fetchSemesters).mockReset().mockResolvedValue(SEMESTERS.map((s) => ({ ...s })));
  mockFetchReg.mockReset().mockResolvedValue(scheduleFixture());
  mockDrop.mockReset().mockResolvedValue({ ...DROP_RESULT });
  mockCourses.mockReset().mockResolvedValue(listingFixture());
});

async function renderDashboard(schedule?: RegistrationSchedule) {
  if (schedule) mockFetchReg.mockReset().mockResolvedValue(schedule);
  const result = renderApp("/dashboard");
  await screen.findByTestId("welcome-message");
  await screen.findByTestId("registered-card");
  await waitFor(() => expect(screen.queryByTestId("registered-loading")).not.toBeInTheDocument());
  await screen.findByTestId("registered-row-1");
  return result;
}

describe("TIDY-6: dashboard focus", () => {
  it("TIDY-6a: clicking Drop moves focus to that row's Confirm button", async () => {
    const { user } = await renderDashboard();
    await user.click(screen.getByTestId("drop-btn-2"));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId("drop-confirm-2")));
  });

  it("TIDY-6b: Cancel returns focus to that row's Drop button", async () => {
    const { user } = await renderDashboard();
    await user.click(screen.getByTestId("drop-btn-1"));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId("drop-confirm-1")));
    await user.click(screen.getByTestId("drop-cancel-1"));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId("drop-btn-1")));
  });

  it("TIDY-6c: after a successful drop focus is on the #drop-feedback wrapper containing the success alert", async () => {
    mockFetchReg.mockReset().mockResolvedValueOnce(scheduleFixture()).mockResolvedValue(onlyCs201());
    const { user } = await renderDashboard();
    await user.click(screen.getByTestId("drop-btn-1"));
    await user.click(screen.getByTestId("drop-confirm-1"));
    await screen.findByTestId("drop-success");
    await waitFor(() => expect(document.activeElement?.id).toBe("drop-feedback"));
    const wrapper = screen.getByTestId("drop-feedback");
    expect(document.activeElement).toBe(wrapper);
    expect(wrapper).toHaveAttribute("tabindex", "-1");
    expect(wrapper).toContainElement(screen.getByTestId("drop-success"));
  });

  it("TIDY-6c: after a 409 drop focus is on the #drop-feedback wrapper containing the error alert", async () => {
    mockDrop.mockReset().mockRejectedValue(new ApiError(409, "Dropping is closed for semester 115-1."));
    const { user } = await renderDashboard();
    await user.click(screen.getByTestId("drop-btn-1"));
    await user.click(screen.getByTestId("drop-confirm-1"));
    await screen.findByTestId("drop-error");
    await waitFor(() => expect(document.activeElement?.id).toBe("drop-feedback"));
    expect(screen.getByTestId("drop-feedback")).toContainElement(screen.getByTestId("drop-error"));
  });

  it("TIDY-6c: the wrapper is not rendered before a drop, and Cancel does not move focus to it", async () => {
    const { user } = await renderDashboard();
    expect(screen.queryByTestId("drop-feedback")).not.toBeInTheDocument();
    await user.click(screen.getByTestId("drop-btn-1"));
    await user.click(screen.getByTestId("drop-cancel-1"));
    expect(screen.queryByTestId("drop-feedback")).not.toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId("drop-btn-1")));
  });

  it("TIDY-6d: closed period gives every Drop button aria-describedby and the note has that id and text", async () => {
    const s = scheduleFixture();
    s.registrationPeriod = { ...CLOSED_PERIOD };
    await renderDashboard(s);
    for (const id of [1, 2]) {
      expect(screen.getByTestId(`drop-btn-${id}`)).toHaveAttribute("aria-describedby", "drop-closed-note");
    }
    const note = document.getElementById("drop-closed-note");
    expect(note).not.toBeNull();
    expect(note?.textContent).toContain("Dropping courses is only possible during the registration period.");
    expect(screen.getByTestId("drop-closed-note")).toBe(note);
  });

  it("TIDY-6d: open period gives the Drop buttons no aria-describedby", async () => {
    const s = scheduleFixture();
    s.registrationPeriod = { ...OPEN_PERIOD };
    await renderDashboard(s);
    for (const id of [1, 2]) {
      expect(screen.getByTestId(`drop-btn-${id}`)).not.toHaveAttribute("aria-describedby");
    }
    expect(document.getElementById("drop-closed-note")).toBeNull();
  });
});

function listing(opts: { fullIds?: number[]; registeredIds?: number[]; open: boolean }): CourseListing {
  const l = listingFixture();
  l.registrationPeriod = { ...(opts.open ? OPEN_PERIOD : CLOSED_PERIOD) };
  for (const c of l.courses) {
    c.availableSeats = opts.fullIds?.includes(c.id) ? 0 : 10;
    c.registered = opts.registeredIds?.includes(c.id) ?? false;
  }
  return l;
}

async function renderCourses(l: CourseListing) {
  mockCourses.mockReset().mockResolvedValue(l);
  const result = renderApp("/courses");
  await screen.findByTestId("courses-title");
  await screen.findByTestId("course-row-1");
  return result;
}

describe("TIDY-6e: Register aria-describedby on /courses", () => {
  it("TIDY-6e: full and open lists only the full note", async () => {
    await renderCourses(listing({ fullIds: [1], open: true }));
    expect(screen.getByTestId("full-note-1")).toHaveAttribute("id", "full-note-1");
    expect(screen.getByTestId("register-btn-1")).toHaveAttribute("aria-describedby", "full-note-1");
  });

  it("TIDY-6e: not full and closed lists only the closed note", async () => {
    await renderCourses(listing({ open: false }));
    expect(screen.getByTestId("closed-note-2")).toHaveAttribute("id", "closed-note-2");
    expect(screen.getByTestId("register-btn-2")).toHaveAttribute("aria-describedby", "closed-note-2");
  });

  it("TIDY-6e: full and closed lists full note then closed note", async () => {
    await renderCourses(listing({ fullIds: [1], open: false }));
    expect(screen.getByTestId("register-btn-1")).toHaveAttribute("aria-describedby", "full-note-1 closed-note-1");
  });

  it("TIDY-6e: available and open has no aria-describedby attribute", async () => {
    await renderCourses(listing({ open: true }));
    expect(screen.getByTestId("register-btn-2")).not.toHaveAttribute("aria-describedby");
  });

  it("TIDY-6e: an already registered course has no aria-describedby, even if full or closed", async () => {
    await renderCourses(listing({ fullIds: [3], registeredIds: [3], open: false }));
    expect(screen.queryByTestId("full-note-3")).not.toBeInTheDocument();
    expect(screen.queryByTestId("closed-note-3")).not.toBeInTheDocument();
    expect(screen.getByTestId("register-btn-3")).not.toHaveAttribute("aria-describedby");
  });
});

describe("TIDY-6f: .full-note font size", () => {
  it("TIDY-6f: index.css .full-note block declares font-size: 13px", () => {
    const css = readFileSync(resolve(__dirname, "../index.css"), "utf8");
    const m = css.match(/\.full-note\s*\{([^}]*)\}/);
    expect(m).not.toBeNull();
    expect(m![1]).toMatch(/font-size:\s*13px/);
    const closed = css.match(/\.closed-note\s*\{([^}]*)\}/);
    if (closed) expect(closed[1]).toMatch(/font-size:\s*12px/);
  });
});
