// FR2 / FR3 frontend criteria (API modules mocked): FR2-UI1..UI7, FR3-UI1..UI7, NFR5-AC1, NFR5-AC2.
import { screen, waitFor, within } from "@testing-library/react";
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
import { REG_RESULT, SEMESTERS, deferred, listingFixture, springListing } from "./coursesFixtures";

const mockFetchMe = vi.mocked(fetchMe);
const mockFetchDashboard = vi.mocked(fetchDashboard);
const mockSemesters = vi.mocked(fetchSemesters);
const mockCourses = vi.mocked(fetchCourses);
const mockRegister = vi.mocked(registerForCourse);

beforeEach(() => {
  mockFetchMe.mockReset().mockResolvedValue({ ...ALICE });
  mockFetchDashboard.mockReset().mockResolvedValue(dashboardExample());
  mockSemesters.mockReset().mockResolvedValue(SEMESTERS.map((s) => ({ ...s })));
  mockCourses.mockReset().mockImplementation(async (params) =>
    params?.semester === "114-2" ? springListing() : listingFixture(),
  );
  mockRegister.mockReset().mockResolvedValue({ ...REG_RESULT });
});

async function renderCourses(listing?: CourseListing) {
  if (listing) mockCourses.mockReset().mockResolvedValue(listing);
  const result = renderApp("/courses");
  await screen.findByTestId("courses-title");
  return result;
}

async function renderTable() {
  const result = await renderCourses();
  await screen.findByTestId("course-row-1");
  return result;
}

/** Arguments of the n-th (0-based) fetchCourses call. */
function callArg(n: number) {
  return mockCourses.mock.calls[n][0] as Record<string, unknown>;
}
function lastArg() {
  return callArg(mockCourses.mock.calls.length - 1);
}

describe("FR2: view courses", () => {
  it("FR2-UI1: side nav has exactly Dashboard and Courses; clicking Courses loads the page", async () => {
    const { user } = renderApp("/dashboard");
    await screen.findByTestId("welcome-message");

    const items = within(screen.getByTestId("side-nav")).getAllByRole("menuitem");
    expect(items).toHaveLength(2);
    expect(items[0].textContent?.trim()).toBe("Dashboard");
    expect(items[1].textContent?.trim()).toBe("Courses");

    await user.click(items[1]);

    expect(await screen.findByTestId("courses-title")).toHaveTextContent("Available Courses");
    expect(mockSemesters).toHaveBeenCalledTimes(1);
    expect(mockCourses).toHaveBeenCalledTimes(1);
    expect(Object.keys(callArg(0))).toHaveLength(0);
  });

  it("FR2-UI1: on /courses the Courses item is the selected one", async () => {
    await renderCourses();
    const items = within(screen.getByTestId("side-nav")).getAllByRole("menuitem");
    const courses = items.find((i) => i.textContent?.trim() === "Courses")!;
    const dashboard = items.find((i) => i.textContent?.trim() === "Dashboard")!;
    expect(courses.className).toMatch(/selected/);
    expect(dashboard.className).not.toMatch(/selected/);
  });

  it("FR2-UI2: table shows headers, three rows in order and exact cell texts", async () => {
    await renderTable();
    const table = screen.getByTestId("courses-table");
    for (const h of ["Code", "Name", "Credits", "Instructor", "Available Seats", "Action"]) {
      expect(within(table).getByRole("columnheader", { name: h })).toBeInTheDocument();
    }
    const rows = Array.from(table.querySelectorAll<HTMLElement>('[data-testid^="course-row-"]'));
    expect(rows.map((r) => r.getAttribute("data-testid"))).toEqual([
      "course-row-1",
      "course-row-2",
      "course-row-3",
    ]);
    expect(screen.getByTestId("course-code-1").textContent).toBe("CS101");
    expect(screen.getByTestId("course-name-1").textContent).toBe("Introduction to Programming");
    expect(screen.getByTestId("course-credits-1").textContent).toBe("3");
    expect(screen.getByTestId("course-instructor-1").textContent).toBe("Prof. Lee");
    expect(screen.getByTestId("course-seats-1").textContent).toBe("50");
    expect(screen.getByTestId("course-seats-3").textContent).toBe("0");
  });

  it("FR2-UI3: semester select has labelled options, current value, and reloads on change", async () => {
    const { user } = await renderTable();
    const select = screen.getByLabelText("Semester") as HTMLSelectElement;
    expect(select).toBe(screen.getByTestId("semester-select"));
    expect(select.tagName).toBe("SELECT");
    await waitFor(() => expect(select.options.length).toBe(2));
    expect(Array.from(select.options).map((o) => [o.value, o.textContent])).toEqual([
      ["115-1", "2026 Fall (115-1)"],
      ["114-2", "2026 Spring (114-2)"],
    ]);
    expect(select.value).toBe("115-1");

    await user.selectOptions(select, "114-2");

    expect(await screen.findByTestId("course-row-4")).toBeInTheDocument();
    expect(Object.keys(lastArg()).sort()).toEqual(["semester"]);
    expect(lastArg().semester).toBe("114-2");
    expect(screen.queryByTestId("course-row-1")).not.toBeInTheDocument();
  });

  it("FR2-UI4: typing and clicking Search calls fetchCourses with semester and q", async () => {
    const { user } = await renderTable();
    expect(screen.getByTestId("course-search-button")).toHaveTextContent("Search");
    await user.type(screen.getByTestId("course-search-input"), "cs");
    await user.click(screen.getByTestId("course-search-button"));

    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
    expect(lastArg()).toEqual({ semester: "115-1", q: "cs" });
    expect(Object.keys(lastArg()).sort()).toEqual(["q", "semester"]);
    expect((screen.getByTestId("semester-select") as HTMLSelectElement).value).toBe("115-1");
  });

  it("FR2-UI4: pressing Enter in the input searches", async () => {
    const { user } = await renderTable();
    await user.type(screen.getByTestId("course-search-input"), "cs{Enter}");

    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
    expect(lastArg()).toEqual({ semester: "115-1", q: "cs" });
  });

  it("FR2-UI4: empty or whitespace-only input sends no q key; text is trimmed", async () => {
    const { user } = await renderTable();
    await user.click(screen.getByTestId("course-search-button"));
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
    expect(Object.keys(lastArg())).toEqual(["semester"]);

    await user.type(screen.getByTestId("course-search-input"), "   ");
    await user.click(screen.getByTestId("course-search-button"));
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(3));
    expect(Object.keys(lastArg())).toEqual(["semester"]);

    await user.clear(screen.getByTestId("course-search-input"));
    await user.type(screen.getByTestId("course-search-input"), "  cs ");
    await user.click(screen.getByTestId("course-search-button"));
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(4));
    expect(lastArg()).toEqual({ semester: "115-1", q: "cs" });
  });

  it("FR2-UI4: changing semester keeps the last applied search text", async () => {
    const { user } = await renderTable();
    await user.type(screen.getByTestId("course-search-input"), "cs");
    await user.click(screen.getByTestId("course-search-button"));
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));

    const select = screen.getByTestId("semester-select") as HTMLSelectElement;
    await waitFor(() => expect(select.options.length).toBe(2));
    await user.selectOptions(select, "114-2");

    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(3));
    expect(lastArg()).toEqual({ semester: "114-2", q: "cs" });
  });

  it("FR2-UI5: empty course list shows 'No courses found.' and no rows", async () => {
    await renderCourses({ semester: { ...SEMESTERS[0] }, courses: [] });
    expect(await screen.findByTestId("courses-empty")).toHaveTextContent("No courses found.");
    expect(document.querySelector('[data-testid^="course-row-"]')).toBeNull();
  });

  it("FR2-UI6: no semester shows 'No semester has been selected.' and a placeholder option", async () => {
    await renderCourses({ semester: null, courses: [] });
    expect(await screen.findByTestId("courses-empty")).toHaveTextContent(
      "No semester has been selected.",
    );
    const select = screen.getByTestId("semester-select") as HTMLSelectElement;
    await waitFor(() => expect(select.options.length).toBeGreaterThanOrEqual(1));
    expect(select.options[0].value).toBe("");
    expect(select.options[0].textContent).toBe("Select a semester");
    expect(select.value).toBe("");
  });

  it("FR2-UI7: shows loading while pending", async () => {
    const d = deferred<CourseListing>();
    mockCourses.mockReset().mockReturnValue(d.promise);
    renderApp("/courses");
    expect(await screen.findByTestId("courses-loading")).toBeInTheDocument();
    d.resolve(listingFixture());
    expect(await screen.findByTestId("course-row-1")).toBeInTheDocument();
    expect(screen.queryByTestId("courses-loading")).not.toBeInTheDocument();
  });

  it("FR2-UI7: a 500 shows the error and keeps the shell", async () => {
    mockCourses.mockReset().mockRejectedValue(new ApiError(500, "An unexpected error occurred."));
    renderApp("/courses");
    expect(await screen.findByTestId("courses-error")).toHaveTextContent(
      "Unable to load courses. Please try again later.",
    );
    expect(screen.getByTestId("app-title")).toBeInTheDocument();
    expect(screen.getByTestId("side-nav")).toBeInTheDocument();
    expect(screen.queryByTestId("login-card")).not.toBeInTheDocument();
  });

  it("FR2-UI7: a non-ApiError failure shows the error too", async () => {
    mockCourses.mockReset().mockRejectedValue(new TypeError("Failed to fetch"));
    renderApp("/courses");
    expect(await screen.findByTestId("courses-error")).toHaveTextContent(
      "Unable to load courses. Please try again later.",
    );
  });

  it("FR2-UI7: a 401 shows the login card", async () => {
    mockCourses.mockReset().mockRejectedValue(new ApiError(401, "Authentication required."));
    renderApp("/courses");
    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
  });
});

describe("FR3: register", () => {
  it("FR3-UI1: clicking Register calls the API, shows success, updates seats, tag and disables button", async () => {
    const { user } = await renderTable();
    const btn = screen.getByTestId("register-btn-1");
    expect(btn.tagName).toBe("BUTTON");
    expect(btn).toHaveTextContent("Register");
    expect(btn).toBeEnabled();

    await user.click(btn);

    expect(await screen.findByTestId("register-success")).toHaveTextContent(
      "Successfully registered for CS101.",
    );
    expect(mockRegister).toHaveBeenCalledTimes(1);
    expect(mockRegister.mock.calls[0]).toEqual([1]);
    expect(screen.getByTestId("course-seats-1").textContent).toBe("49");
    expect(screen.getByTestId("registered-tag-1")).toHaveTextContent("Registered");
    expect(screen.getByTestId("register-btn-1")).toBeDisabled();
  });

  it("FR3-UI2: registered course shows tag and disabled button; click does nothing", async () => {
    const { user } = await renderTable();
    expect(screen.getByTestId("registered-tag-3")).toHaveTextContent("Registered");
    expect(screen.getByTestId("register-btn-3")).toBeDisabled();
    expect(screen.queryByTestId("registered-tag-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("registered-tag-2")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("register-btn-3"));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it("FR3-UI3: while pending the button is disabled, a second click is ignored, other rows stay enabled", async () => {
    const d = deferred<typeof REG_RESULT>();
    mockRegister.mockReset().mockReturnValue(d.promise);
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-1"));
    await waitFor(() => expect(screen.getByTestId("register-btn-1")).toBeDisabled());
    await user.click(screen.getByTestId("register-btn-1"));

    expect(mockRegister).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("register-btn-2")).toBeEnabled();

    d.resolve({ ...REG_RESULT });
    expect(await screen.findByTestId("register-success")).toBeInTheDocument();
  });

  it("FR3-UI4: a 409 shows the server message, no success alert, and refetches with current filters", async () => {
    mockRegister.mockReset().mockRejectedValue(new ApiError(409, "Course CS101 is full."));
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-error")).toHaveTextContent("Course CS101 is full.");
    expect(screen.queryByTestId("register-success")).not.toBeInTheDocument();
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
    expect(lastArg()).toEqual({ semester: "115-1" });
    // feedback stays after refetch
    await screen.findByTestId("course-row-1");
    expect(screen.getByTestId("register-error")).toHaveTextContent("Course CS101 is full.");
  });

  it("FR3-UI4: any other 4xx (404) shows its own message and refetches", async () => {
    mockRegister.mockReset().mockRejectedValue(new ApiError(404, "Course not found."));
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-2"));

    expect(await screen.findByTestId("register-error")).toHaveTextContent("Course not found.");
    await waitFor(() => expect(mockCourses).toHaveBeenCalledTimes(2));
  });

  it("FR3-UI5: a 5xx shows the generic message and does not refetch", async () => {
    mockRegister.mockReset().mockRejectedValue(new ApiError(500, "An unexpected error occurred."));
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-error")).toHaveTextContent(
      "Unable to register. Please try again later.",
    );
    expect(mockCourses).toHaveBeenCalledTimes(1);
  });

  it("FR3-UI5: a non-ApiError failure shows the generic message and does not refetch", async () => {
    mockRegister.mockReset().mockRejectedValue(new TypeError("Failed to fetch"));
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-error")).toHaveTextContent(
      "Unable to register. Please try again later.",
    );
    expect(mockCourses).toHaveBeenCalledTimes(1);
  });

  it("FR3-UI5: a 401 shows the login card", async () => {
    mockRegister.mockReset().mockRejectedValue(new ApiError(401, "Authentication required."));
    const { user } = await renderTable();

    await user.click(screen.getByTestId("register-btn-1"));

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
  });

  it("FR3-UI6: starting a new registration clears the previous feedback", async () => {
    const { user } = await renderTable();
    await user.click(screen.getByTestId("register-btn-1"));
    await screen.findByTestId("register-success");

    const d = deferred<typeof REG_RESULT>();
    mockRegister.mockReset().mockReturnValue(d.promise);
    await user.click(screen.getByTestId("register-btn-2"));

    await waitFor(() => expect(screen.queryByTestId("register-success")).not.toBeInTheDocument());
    expect(screen.queryByTestId("register-error")).not.toBeInTheDocument();
    d.resolve({ ...REG_RESULT, courseId: 2, courseCode: "CS201", message: "Successfully registered for CS201." });
    expect(await screen.findByTestId("register-success")).toHaveTextContent("CS201");
    expect(screen.queryByTestId("register-error")).not.toBeInTheDocument();
  });

  it("FR3-UI6: clicking Search clears the feedback", async () => {
    const { user } = await renderTable();
    await user.click(screen.getByTestId("register-btn-1"));
    await screen.findByTestId("register-success");

    await user.click(screen.getByTestId("course-search-button"));

    await waitFor(() => expect(screen.queryByTestId("register-success")).not.toBeInTheDocument());
  });

  it("FR3-UI6: changing the semester clears the feedback", async () => {
    mockRegister.mockReset().mockRejectedValue(new ApiError(409, "Course CS101 is full."));
    const { user } = await renderTable();
    await user.click(screen.getByTestId("register-btn-1"));
    await screen.findByTestId("register-error");
    await screen.findByTestId("course-row-1");

    const select = screen.getByTestId("semester-select") as HTMLSelectElement;
    await waitFor(() => expect(select.options.length).toBe(2));
    await user.selectOptions(select, "114-2");

    await waitFor(() => expect(screen.queryByTestId("register-error")).not.toBeInTheDocument());
    expect(screen.queryByTestId("register-success")).not.toBeInTheDocument();
  });

  it("FR3-UI7: a full unregistered course keeps an enabled Register button and calls the API", async () => {
    const listing = listingFixture();
    listing.courses[2].registered = false;
    mockRegister.mockReset().mockRejectedValue(new ApiError(409, "Course MA101 is full."));
    const { user } = await renderCourses(listing);
    await screen.findByTestId("course-row-3");

    expect(screen.getByTestId("course-seats-3").textContent).toBe("0");
    expect(screen.getByTestId("register-btn-3")).toBeEnabled();

    await user.click(screen.getByTestId("register-btn-3"));

    expect(mockRegister).toHaveBeenCalledTimes(1);
    expect(mockRegister.mock.calls[0]).toEqual([3]);
    expect(await screen.findByTestId("register-error")).toHaveTextContent("Course MA101 is full.");
  });
});

describe("NFR5: usability", () => {
  it("NFR5-AC1: Courses then Register takes fewer than 5 clicks", async () => {
    const { user } = renderApp("/dashboard");
    await screen.findByTestId("welcome-message");
    let clicks = 0;
    const click = async (el: Element) => {
      clicks++;
      await user.click(el);
    };

    const items = within(screen.getByTestId("side-nav")).getAllByRole("menuitem");
    await click(items.find((i) => i.textContent?.trim() === "Courses")!);
    await click(await screen.findByTestId("register-btn-1"));

    expect(await screen.findByTestId("register-success")).toHaveTextContent(
      "Successfully registered for CS101.",
    );
    expect(clicks).toBeLessThan(5);
  });

  it("NFR5-AC2: no confirmation dialog after a single Register click", async () => {
    const { user } = await renderTable();
    await user.click(screen.getByTestId("register-btn-1"));

    expect(mockRegister).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(document.querySelector(".ant-modal, .ant-popover, .ant-popconfirm")).toBeNull();
    await screen.findByTestId("register-success");
  });
});
