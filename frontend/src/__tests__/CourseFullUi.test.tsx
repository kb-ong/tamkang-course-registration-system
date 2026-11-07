// FR6 frontend criteria (API modules mocked): FR6-UI1 .. FR6-UI8.
import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/auth", () => ({ login: vi.fn(), logout: vi.fn(), fetchMe: vi.fn() }));
vi.mock("../api/dashboard", () => ({ fetchDashboard: vi.fn() }));
vi.mock("../api/courses", () => ({
  fetchSemesters: vi.fn(),
  fetchCourses: vi.fn(),
  registerForCourse: vi.fn(),
}));

import { fetchMe } from "../api/auth";
import { fetchDashboard } from "../api/dashboard";
import { fetchCourses, fetchSemesters, registerForCourse } from "../api/courses";
import { ApiError } from "../api/client";
import type { CourseListing } from "../api/types";
import { ALICE, dashboardExample, renderApp } from "./fixtures";
import { REG_RESULT, SEMESTERS, listingFixture } from "./coursesFixtures";

const mockFetchMe = vi.mocked(fetchMe);
const mockFetchDashboard = vi.mocked(fetchDashboard);
const mockSemesters = vi.mocked(fetchSemesters);
const mockCourses = vi.mocked(fetchCourses);
const mockRegister = vi.mocked(registerForCourse);

/** The "full fixture": MA101 (id 3, 1/0) is NOT registered. */
function fullFixture(): CourseListing {
  const l = listingFixture();
  l.courses[2].registered = false;
  return l;
}

beforeEach(() => {
  mockFetchMe.mockReset().mockResolvedValue({ ...ALICE });
  mockFetchDashboard.mockReset().mockResolvedValue(dashboardExample());
  mockSemesters.mockReset().mockResolvedValue(SEMESTERS.map((s) => ({ ...s })));
  mockCourses.mockReset().mockResolvedValue(fullFixture());
  mockRegister.mockReset().mockResolvedValue({ ...REG_RESULT });
});

async function renderTable(listing?: CourseListing) {
  if (listing) mockCourses.mockReset().mockResolvedValue(listing);
  const result = renderApp("/courses");
  await screen.findByTestId("courses-title");
  await screen.findByTestId("course-row-1");
  return result;
}

describe("FR6: full course UI", () => {
  it("FR6-UI1: a full course shows the red Full tag; others do not; seat counts stay exact digits", async () => {
    await renderTable();
    expect(screen.getByTestId("full-tag-3")).toHaveTextContent(/^Full$/);
    expect(screen.queryByTestId("full-tag-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("full-tag-2")).not.toBeInTheDocument();
    expect(screen.getByTestId("course-seats-3").textContent).toBe("0");
    expect(screen.getByTestId("course-seats-1").textContent).toBe("50");
    // the Full tag lives outside the seats span
    expect(screen.getByTestId("course-seats-3")).not.toContainElement(screen.getByTestId("full-tag-3"));
  });

  it("FR6-UI2: full course has a disabled Register button, the note, and clicking does not call the API", async () => {
    const { user } = await renderTable();
    const btn = screen.getByTestId("register-btn-3");
    expect(btn).toBeDisabled();
    expect(btn).toHaveTextContent("Register");
    expect(screen.getByTestId("full-note-3")).toHaveTextContent(/^No seats available\.$/);

    await user.click(btn);
    expect(mockRegister).not.toHaveBeenCalled();

    expect(screen.getByTestId("register-btn-1")).toBeEnabled();
    expect(screen.queryByTestId("full-note-1")).not.toBeInTheDocument();
  });

  it("FR6-UI3: full and registered shows Full and Registered, disabled button, no note", async () => {
    await renderTable(listingFixture()); // MA101 registered: true
    expect(screen.getByTestId("full-tag-3")).toHaveTextContent(/^Full$/);
    expect(screen.getByTestId("registered-tag-3")).toHaveTextContent("Registered");
    expect(screen.getByTestId("register-btn-3")).toBeDisabled();
    expect(screen.queryByTestId("full-note-3")).not.toBeInTheDocument();
  });

  it("FR6-UI4: a course that fills between load and click shows the server message and turns full after the refetch", async () => {
    const first = fullFixture();
    first.courses[0].availableSeats = 1;
    const second = fullFixture();
    second.courses[0].availableSeats = 0;
    second.courses[0].registered = false;
    mockCourses.mockReset().mockResolvedValueOnce(first).mockResolvedValue(second);
    mockRegister
      .mockReset()
      .mockRejectedValue(new ApiError(409, "Course Full: CS101 has no available seats."));

    const { user } = await renderTable();
    expect(screen.queryByTestId("full-tag-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("register-btn-1")).toBeEnabled();

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-error")).toHaveTextContent(
      "Course Full: CS101 has no available seats.",
    );
    expect(screen.queryByTestId("register-success")).not.toBeInTheDocument();
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
    expect(mockCourses.mock.calls[1][0]).toEqual({ semester: "115-1" });

    expect(await screen.findByTestId("full-tag-1")).toHaveTextContent(/^Full$/);
    expect(screen.getByTestId("register-btn-1")).toBeDisabled();
    expect(screen.getByTestId("course-seats-1").textContent).toBe("0");
    expect(screen.getByTestId("full-note-1")).toHaveTextContent("No seats available.");
    expect(screen.getByTestId("register-error")).toHaveTextContent(
      "Course Full: CS101 has no available seats.",
    );
  });

  it("FR6-UI5: registering the last seat makes the row full with Registered and no note", async () => {
    const listing = fullFixture();
    listing.courses[0].availableSeats = 1;
    mockRegister.mockReset().mockResolvedValue({
      message: "Successfully registered for CS101.",
      registrationId: 10,
      courseId: 1,
      courseCode: "CS101",
      availableSeats: 0,
    });
    const { user } = await renderTable(listing);

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-success")).toBeInTheDocument();
    expect(screen.getByTestId("course-seats-1").textContent).toBe("0");
    expect(screen.getByTestId("registered-tag-1")).toBeInTheDocument();
    expect(screen.getByTestId("full-tag-1")).toHaveTextContent(/^Full$/);
    expect(screen.getByTestId("register-btn-1")).toBeDisabled();
    expect(screen.queryByTestId("full-note-1")).not.toBeInTheDocument();
  });

  it("FR6-UI6: availableSeats <= 0 counts as full even when the value is negative", async () => {
    const listing = fullFixture();
    listing.courses[0].capacity = 5;
    listing.courses[0].availableSeats = -1;
    await renderTable(listing);
    expect(screen.getByTestId("full-tag-1")).toBeInTheDocument();
    expect(screen.getByTestId("full-note-1")).toHaveTextContent("No seats available.");
    expect(screen.getByTestId("register-btn-1")).toBeDisabled();
  });

  it("FR6-UI7: the server's 409 message is shown as is, whatever its text, and the list is refetched", async () => {
    mockRegister
      .mockReset()
      .mockRejectedValue(new ApiError(409, "You are already registered for CS101."));
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-error")).toHaveTextContent(
      "You are already registered for CS101.",
    );
    expect(screen.getByTestId("register-error")).not.toHaveTextContent("Course Full");
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
  });

  it("FR6-UI8: after switching to 114-2 a full course shows the same full state", async () => {
    const spring: CourseListing = {
      semester: { ...SEMESTERS[1] },
      courses: [
        { id: 4, code: "CS102", name: "Object-Oriented Programming", credits: 3, instructor: "Prof. Lee", capacity: 45, availableSeats: 0, registered: false },
      ],
    };
    mockCourses.mockReset().mockImplementation(async (params) =>
      params?.semester === "114-2" ? spring : fullFixture(),
    );
    const { user } = await renderTable();
    const select = screen.getByTestId("semester-select") as HTMLSelectElement;
    await waitFor(() => expect(select.options.length).toBe(2));

    await user.selectOptions(select, "114-2");

    expect(await screen.findByTestId("course-row-4")).toBeInTheDocument();
    expect(screen.getByTestId("full-tag-4")).toHaveTextContent(/^Full$/);
    expect(screen.getByTestId("full-note-4")).toHaveTextContent("No seats available.");
    expect(screen.getByTestId("register-btn-4")).toBeDisabled();
  });
});
