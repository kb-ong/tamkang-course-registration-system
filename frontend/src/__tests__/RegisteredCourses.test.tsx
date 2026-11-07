// FR5 / FR4 / FR7-UI9 / NFR5-AC3 frontend criteria: the dashboard "My registered courses" card.
import { screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/auth", () => ({ login: vi.fn(), logout: vi.fn(), fetchMe: vi.fn() }));
vi.mock("../api/dashboard", () => ({ fetchDashboard: vi.fn() }));
vi.mock("../api/registrations", () => ({ fetchRegistrations: vi.fn(), dropCourse: vi.fn() }));
vi.mock("../utils/download", () => ({ downloadTextFile: vi.fn() }));

import { fetchMe } from "../api/auth";
import { fetchDashboard } from "../api/dashboard";
import { dropCourse, fetchRegistrations } from "../api/registrations";
import { downloadTextFile } from "../utils/download";
import { buildScheduleCsv } from "../utils/scheduleCsv";
import { ApiError } from "../api/client";
import type { RegistrationSchedule } from "../api/types";
import { ALICE, dashboardExample, renderApp } from "./fixtures";
import { deferred } from "./coursesFixtures";
import {
  CLOSED_PERIOD,
  DROP_RESULT,
  NO_PERIOD,
  OPEN_PERIOD,
  emptySchedule,
  onlyCs201,
  scheduleFixture,
  springSchedule,
} from "./scheduleFixtures";

const mockFetchReg = vi.mocked(fetchRegistrations);
const mockDrop = vi.mocked(dropCourse);
const mockDownload = vi.mocked(downloadTextFile);

beforeEach(() => {
  vi.mocked(fetchMe).mockReset().mockResolvedValue({ ...ALICE });
  vi.mocked(fetchDashboard).mockReset().mockResolvedValue(dashboardExample());
  mockFetchReg.mockReset().mockResolvedValue(scheduleFixture());
  mockDrop.mockReset().mockResolvedValue({ ...DROP_RESULT });
  mockDownload.mockReset();
});

async function renderDashboard(schedule?: RegistrationSchedule) {
  if (schedule) mockFetchReg.mockReset().mockResolvedValue(schedule);
  const result = renderApp("/dashboard");
  await screen.findByTestId("welcome-message");
  await screen.findByTestId("registered-card");
  return result;
}

/** Renders and waits until the data (not the loading state) is displayed. */
async function renderLoaded(schedule?: RegistrationSchedule) {
  const result = await renderDashboard(schedule);
  await waitFor(() => expect(screen.queryByTestId("registered-loading")).not.toBeInTheDocument());
  return result;
}

async function dropRow1(user: ReturnType<typeof renderApp>["user"]) {
  await user.click(screen.getByTestId("drop-btn-1"));
  await user.click(screen.getByTestId("drop-confirm-1"));
}

describe("FR5: registered courses card", () => {
  it("FR5-UI1: the card lists the registered courses with headers and exact cell values", async () => {
    await renderLoaded();
    const card = screen.getByTestId("registered-card");
    expect(card).toHaveTextContent("My registered courses");
    expect(mockFetchReg).toHaveBeenCalledTimes(1);
    expect(mockFetchReg.mock.calls[0][0]).toBeUndefined();

    const table = await screen.findByTestId("registered-table");
    const headers = within(table).getAllByRole("columnheader").map((h) => h.textContent?.trim());
    expect(headers).toEqual(["Code", "Name", "Credits", "Instructor", "Registered On", "Action"]);
    const r1 = screen.getByTestId("registered-row-1");
    const r2 = screen.getByTestId("registered-row-2");
    expect(r1.compareDocumentPosition(r2) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByTestId("registered-code-1").textContent).toBe("CS101");
    expect(screen.getByTestId("registered-name-1").textContent).toBe("Introduction to Programming");
    expect(screen.getByTestId("registered-credits-1").textContent).toBe("3");
    expect(screen.getByTestId("registered-instructor-1").textContent).toBe("Prof. Lee");
    expect(screen.getByTestId("registered-date-1").textContent).toBe("2026-09-01 09:00");
    expect(screen.getByTestId("profile-card")).toBeInTheDocument();
    expect(screen.getByTestId("semester-card")).toBeInTheDocument();
  });

  it("FR5-UI2: total credits is the API value, not computed by the UI, with its label", async () => {
    await renderLoaded();
    expect((await screen.findByTestId("total-credits")).textContent).toBe("6");
    expect(screen.getByTestId("registered-card")).toHaveTextContent("Total credit hours:");
  });

  it("FR5-UI2: a totalCredits of 99 is shown as 99", async () => {
    const s = scheduleFixture();
    s.totalCredits = 99;
    await renderLoaded(s);
    expect((await screen.findByTestId("total-credits")).textContent).toBe("99");
  });

  it("FR5-UI2: with no courses and total 0 it shows 0", async () => {
    await renderLoaded(emptySchedule());
    expect((await screen.findByTestId("total-credits")).textContent).toBe("0");
  });

  it("FR5-UI3: while fetchRegistrations is pending registered-loading is shown", async () => {
    const d = deferred<RegistrationSchedule>();
    mockFetchReg.mockReset().mockReturnValue(d.promise);
    renderApp("/dashboard");
    expect(await screen.findByTestId("registered-loading")).toBeInTheDocument();
    d.resolve(scheduleFixture());
    await screen.findByTestId("registered-table");
    expect(screen.queryByTestId("registered-loading")).not.toBeInTheDocument();
  });

  it("FR5-UI3: no courses shows the empty text and no table", async () => {
    await renderLoaded(emptySchedule());
    expect(await screen.findByTestId("registered-empty")).toHaveTextContent(
      "You have not registered for any courses in this semester.",
    );
    expect(screen.queryByTestId("registered-table")).not.toBeInTheDocument();
  });

  it("FR5-UI3: semester null shows 'No semester has been selected.'", async () => {
    await renderLoaded({ semester: null, semesters: scheduleFixture().semesters, registrationPeriod: null, courses: [], totalCredits: 0 });
    expect(await screen.findByTestId("registered-empty")).toHaveTextContent("No semester has been selected.");
  });

  it("FR5-UI3: an ApiError 500 shows registered-error and keeps the profile card", async () => {
    mockFetchReg.mockReset().mockRejectedValue(new ApiError(500, "An unexpected error occurred."));
    renderApp("/dashboard");
    expect(await screen.findByTestId("registered-error")).toHaveTextContent(
      "Unable to load your registered courses. Please try again later.",
    );
    expect(screen.getByTestId("profile-card")).toBeInTheDocument();
    expect(screen.getByTestId("semester-card")).toBeInTheDocument();
  });

  it("FR5-UI3: a non-ApiError failure shows registered-error", async () => {
    mockFetchReg.mockReset().mockRejectedValue(new TypeError("Failed to fetch"));
    renderApp("/dashboard");
    expect(await screen.findByTestId("registered-error")).toHaveTextContent(
      "Unable to load your registered courses. Please try again later.",
    );
    expect(screen.getByTestId("profile-card")).toBeInTheDocument();
  });

  it("FR5-UI3: an ApiError 401 sends the user to the login card", async () => {
    mockFetchReg.mockReset().mockRejectedValue(new ApiError(401, "Authentication required."));
    renderApp("/dashboard");
    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
  });

  it("FR5-UI4: the semester select is labelled, lists the semesters and reloads on change", async () => {
    mockFetchReg.mockReset().mockImplementation(async (code) => (code === "114-2" ? springSchedule() : scheduleFixture()));
    const { user } = await renderLoaded();
    const card = screen.getByTestId("registered-card");
    const select = screen.getByTestId("registered-semester-select") as HTMLSelectElement;
    expect(select.tagName).toBe("SELECT");
    expect(within(card).getByLabelText("Semester")).toBe(select);
    expect(Array.from(select.options).map((o) => [o.value, o.textContent])).toEqual([
      ["115-1", "2026 Fall (115-1)"],
      ["114-2", "2026 Spring (114-2)"],
    ]);
    expect(select.value).toBe("115-1");

    await user.selectOptions(select, "114-2");

    expect(await screen.findByTestId("registered-row-4")).toBeInTheDocument();
    expect(mockFetchReg).toHaveBeenLastCalledWith("114-2");
    expect(screen.queryByTestId("registered-row-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("total-credits").textContent).toBe("3");
    expect((screen.getByTestId("registered-semester-select") as HTMLSelectElement).value).toBe("114-2");
  });

  it("FR5-UI4: a semester change clears the drop feedback alert", async () => {
    mockFetchReg.mockReset().mockImplementation(async (code) => (code === "114-2" ? springSchedule() : scheduleFixture()));
    const { user } = await renderLoaded();
    await dropRow1(user);
    expect(await screen.findByTestId("drop-success")).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId("registered-semester-select"), "114-2");

    await screen.findByTestId("registered-row-4");
    expect(screen.queryByTestId("drop-success")).not.toBeInTheDocument();
  });

  it("FR5-UI5: Print calls window.print once; the card is a print area with a print header", async () => {
    const printSpy = vi.fn();
    window.print = printSpy;
    const { user } = await renderLoaded();
    const btn = screen.getByTestId("print-btn");
    expect(btn.tagName).toBe("BUTTON");
    expect(btn).toHaveTextContent("Print");
    await user.click(btn);
    expect(printSpy).toHaveBeenCalledTimes(1);

    expect(screen.getByTestId("registered-card")).toHaveClass("print-area");
    const header = screen.getByTestId("print-header");
    expect(header).toHaveClass("print-only");
    expect(within(header).getByText("Alice Chen (410000001)")).toBeInTheDocument();
    expect(within(header).getByText("2026 Fall (115-1)")).toBeInTheDocument();
    expect(within(header).getByText("Total credit hours: 6")).toBeInTheDocument();
  });

  it("FR5-UI6: Download CSV calls downloadTextFile once with the file name and the CSV text", async () => {
    const { user } = await renderLoaded();
    const btn = screen.getByTestId("csv-btn");
    expect(btn).toHaveTextContent("Download CSV");
    await user.click(btn);
    expect(mockDownload).toHaveBeenCalledTimes(1);
    const fx = scheduleFixture();
    expect(mockDownload).toHaveBeenCalledWith(
      "registered-courses-115-1.csv",
      buildScheduleCsv(fx.courses, fx.totalCredits),
    );
    expect(typeof mockDownload.mock.calls[0][1]).toBe("string");
  });

  it("FR5-UI8: Print and CSV are disabled when the list is empty", async () => {
    await renderLoaded(emptySchedule());
    expect(await screen.findByTestId("registered-empty")).toBeInTheDocument();
    expect(screen.getByTestId("print-btn")).toBeDisabled();
    expect(screen.getByTestId("csv-btn")).toBeDisabled();
  });

  it("FR5-UI8: Print and CSV are disabled when semester is null", async () => {
    await renderLoaded({ semester: null, semesters: scheduleFixture().semesters, registrationPeriod: null, courses: [], totalCredits: 0 });
    expect(screen.getByTestId("print-btn")).toBeDisabled();
    expect(screen.getByTestId("csv-btn")).toBeDisabled();
  });

  it("FR5-UI8: Print and CSV are enabled with rows, also when the period is closed", async () => {
    const s = scheduleFixture();
    s.registrationPeriod = { ...CLOSED_PERIOD };
    await renderLoaded(s);
    expect(screen.getByTestId("print-btn")).toBeEnabled();
    expect(screen.getByTestId("csv-btn")).toBeEnabled();
  });
});

describe("FR4: drop course", () => {
  it("FR4-UI1: Drop opens an inline confirm, Cancel closes it, and nothing is dropped", async () => {
    const { user } = await renderLoaded();
    for (const id of [1, 2]) {
      const b = screen.getByTestId(`drop-btn-${id}`);
      expect(b.tagName).toBe("BUTTON");
      expect(b).toHaveTextContent("Drop");
      expect(b).toBeEnabled();
    }
    await user.click(screen.getByTestId("drop-btn-1"));
    expect(screen.getByTestId("drop-confirm-text-1")).toHaveTextContent("Drop CS101?");
    expect(screen.getByTestId("drop-confirm-1")).toHaveTextContent("Confirm drop");
    expect(screen.getByTestId("drop-cancel-1")).toHaveTextContent("Cancel");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mockDrop).not.toHaveBeenCalled();

    await user.click(screen.getByTestId("drop-cancel-1"));
    expect(screen.queryByTestId("drop-confirm-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("drop-confirm-text-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("drop-cancel-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("drop-btn-1")).toBeInTheDocument();
    expect(mockDrop).not.toHaveBeenCalled();
  });

  it("FR4-UI1: opening the confirm on row 2 closes the one on row 1", async () => {
    const { user } = await renderLoaded();
    await user.click(screen.getByTestId("drop-btn-1"));
    expect(screen.getByTestId("drop-confirm-1")).toBeInTheDocument();
    await user.click(screen.getByTestId("drop-btn-2"));
    expect(screen.getByTestId("drop-confirm-2")).toBeInTheDocument();
    expect(screen.queryByTestId("drop-confirm-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("drop-btn-1")).toBeInTheDocument();
  });

  it("FR4-UI2: confirming calls dropCourse(1), shows the success message and refreshes rows and total", async () => {
    mockFetchReg.mockReset().mockResolvedValueOnce(scheduleFixture()).mockResolvedValue(onlyCs201());
    const { user } = await renderLoaded();

    await dropRow1(user);

    expect(await screen.findByTestId("drop-success")).toHaveTextContent("Successfully dropped CS101.");
    expect(mockDrop).toHaveBeenCalledTimes(1);
    expect(mockDrop.mock.calls[0]).toEqual([1]);
    await waitFor(() => expect(screen.queryByTestId("registered-row-1")).not.toBeInTheDocument());
    expect(mockFetchReg).toHaveBeenCalledTimes(2);
    expect(mockFetchReg.mock.calls[1][0]).toBe("115-1");
    expect(screen.getByTestId("registered-row-2")).toBeInTheDocument();
    expect(screen.getByTestId("total-credits").textContent).toBe("3");
    expect(screen.getByTestId("drop-success")).toHaveTextContent("Successfully dropped CS101.");
    expect(screen.queryByTestId("drop-error")).not.toBeInTheDocument();
  });

  it("FR4-UI3: while dropCourse is pending the confirm button is disabled and a second click is ignored", async () => {
    const d = deferred<typeof DROP_RESULT>();
    mockDrop.mockReset().mockReturnValue(d.promise);
    const { user } = await renderLoaded();
    await dropRow1(user);

    // antd `loading` buttons keep the click inert and carry the loading class (or the disabled attribute).
    await waitFor(() => {
      const b = screen.getByTestId("drop-confirm-1");
      expect(b.hasAttribute("disabled") || b.classList.contains("ant-btn-loading")).toBe(true);
    });
    await user.click(screen.getByTestId("drop-confirm-1"));
    expect(mockDrop).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("drop-btn-2")).toBeEnabled();

    d.resolve({ ...DROP_RESULT });
    await screen.findByTestId("drop-success");
  });

  it("FR4-UI4: a 4xx ApiError shows its message, closes the confirm and refetches", async () => {
    mockDrop.mockReset().mockRejectedValue(new ApiError(404, "You are not registered for CS101."));
    const { user } = await renderLoaded();
    await dropRow1(user);

    expect(await screen.findByTestId("drop-error")).toHaveTextContent("You are not registered for CS101.");
    expect(screen.queryByTestId("drop-success")).not.toBeInTheDocument();
    expect(screen.queryByTestId("drop-confirm-1")).not.toBeInTheDocument();
    await waitFor(() => expect(mockFetchReg).toHaveBeenCalledTimes(2));
    expect(mockFetchReg.mock.calls[1][0]).toBe("115-1");
  });

  it("FR4-UI4: a 409 closed message is shown as is", async () => {
    mockDrop.mockReset().mockRejectedValue(new ApiError(409, "Dropping is closed for semester 115-1."));
    const { user } = await renderLoaded();
    await dropRow1(user);
    expect(await screen.findByTestId("drop-error")).toHaveTextContent("Dropping is closed for semester 115-1.");
    await waitFor(() => expect(mockFetchReg).toHaveBeenCalledTimes(2));
  });

  it("FR4-UI4: a 5xx ApiError shows the generic message and does not refetch", async () => {
    mockDrop.mockReset().mockRejectedValue(new ApiError(500, "An unexpected error occurred."));
    const { user } = await renderLoaded();
    await dropRow1(user);
    expect(await screen.findByTestId("drop-error")).toHaveTextContent(
      "Unable to drop the course. Please try again later.",
    );
    expect(screen.queryByTestId("drop-confirm-1")).not.toBeInTheDocument();
    expect(mockFetchReg).toHaveBeenCalledTimes(1);
  });

  it("FR4-UI4: a non-ApiError shows the generic message and does not refetch", async () => {
    mockDrop.mockReset().mockRejectedValue(new TypeError("Failed to fetch"));
    const { user } = await renderLoaded();
    await dropRow1(user);
    expect(await screen.findByTestId("drop-error")).toHaveTextContent(
      "Unable to drop the course. Please try again later.",
    );
    expect(mockFetchReg).toHaveBeenCalledTimes(1);
  });

  it("FR4-UI4: a 401 ApiError sends the user to the login card", async () => {
    mockDrop.mockReset().mockRejectedValue(new ApiError(401, "Authentication required."));
    const { user } = await renderLoaded();
    await dropRow1(user);
    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
  });

  it("FR4-UI5: success and error are never shown together; starting another drop removes the feedback", async () => {
    mockFetchReg.mockReset().mockResolvedValue(scheduleFixture());
    mockDrop
      .mockReset()
      .mockResolvedValueOnce({ ...DROP_RESULT })
      .mockRejectedValueOnce(new ApiError(404, "You are not registered for CS201."));
    const { user } = await renderLoaded();

    await dropRow1(user);
    expect(await screen.findByTestId("drop-success")).toBeInTheDocument();
    expect(screen.queryByTestId("drop-error")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("drop-btn-2"));
    expect(screen.queryByTestId("drop-success")).not.toBeInTheDocument();
    expect(screen.queryByTestId("drop-error")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("drop-confirm-2"));
    expect(await screen.findByTestId("drop-error")).toHaveTextContent("You are not registered for CS201.");
    expect(screen.queryByTestId("drop-success")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("drop-btn-1"));
    expect(screen.queryByTestId("drop-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("drop-success")).not.toBeInTheDocument();
  });

  it("FR4-UI5: a semester change removes the drop error", async () => {
    mockFetchReg.mockReset().mockImplementation(async (code) => (code === "114-2" ? springSchedule() : scheduleFixture()));
    mockDrop.mockReset().mockRejectedValue(new ApiError(409, "Dropping is closed for semester 115-1."));
    const { user } = await renderLoaded();
    await dropRow1(user);
    expect(await screen.findByTestId("drop-error")).toBeInTheDocument();

    await user.selectOptions(screen.getByTestId("registered-semester-select"), "114-2");
    await screen.findByTestId("registered-row-4");
    expect(screen.queryByTestId("drop-error")).not.toBeInTheDocument();
  });
});

describe("FR7-UI9: closed period on the dashboard", () => {
  it("FR7-UI9: closed period keeps rows, total, print and CSV; Drop is disabled and shows the note", async () => {
    const s = scheduleFixture();
    s.registrationPeriod = { ...CLOSED_PERIOD };
    const { user } = await renderLoaded(s);

    expect(screen.getByTestId("registered-row-1")).toBeInTheDocument();
    expect(screen.getByTestId("registered-row-2")).toBeInTheDocument();
    expect(screen.getByTestId("total-credits").textContent).toBe("6");
    expect(screen.getByTestId("print-btn")).toBeEnabled();
    expect(screen.getByTestId("csv-btn")).toBeEnabled();
    for (const id of [1, 2]) {
      const b = screen.getByTestId(`drop-btn-${id}`);
      expect(b).toBeDisabled();
      expect(b).toHaveTextContent("Drop");
    }
    await user.click(screen.getByTestId("drop-btn-1"));
    expect(screen.queryByTestId("drop-confirm-1")).not.toBeInTheDocument();
    expect(mockDrop).not.toHaveBeenCalled();
    expect(screen.getByTestId("drop-closed-note").textContent?.trim()).toBe(
      "Dropping courses is only possible during the registration period.",
    );
  });

  it("FR7-UI9: with an open period the note is not shown", async () => {
    const s = scheduleFixture();
    s.registrationPeriod = { ...OPEN_PERIOD };
    await renderLoaded(s);
    expect(screen.queryByTestId("drop-closed-note")).not.toBeInTheDocument();
  });

  it("FR7-UI9: with no period scheduled the note is shown", async () => {
    const s = scheduleFixture();
    s.registrationPeriod = { ...NO_PERIOD };
    await renderLoaded(s);
    expect(screen.getByTestId("drop-closed-note")).toBeInTheDocument();
    expect(screen.getByTestId("drop-btn-1")).toBeDisabled();
  });

  it("FR7-UI9: with registrationPeriod null (no semester) the note is not shown", async () => {
    await renderLoaded({ semester: null, semesters: scheduleFixture().semesters, registrationPeriod: null, courses: [], totalCredits: 0 });
    expect(screen.queryByTestId("drop-closed-note")).not.toBeInTheDocument();
  });
});

describe("NFR5: click counts", () => {
  it("NFR5-AC3: dropping a course takes fewer than 5 clicks from the dashboard", async () => {
    const { user } = await renderLoaded();
    let clicks = 0;
    const click = async (id: string) => {
      clicks += 1;
      await user.click(screen.getByTestId(id));
    };
    await click("drop-btn-1");
    await click("drop-confirm-1");

    expect(await screen.findByTestId("drop-success")).toHaveTextContent("Successfully dropped CS101.");
    expect(clicks).toBeLessThan(5);
  });
});
