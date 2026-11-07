// FR1 dashboard and app shell criteria: FR1-UI8 .. FR1-UI13.
import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/auth", () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
}));
vi.mock("../api/dashboard", () => ({
  fetchDashboard: vi.fn(),
}));

import { fetchMe, login, logout } from "../api/auth";
import { fetchDashboard } from "../api/dashboard";
import { ApiError } from "../api/client";
import type { DashboardData } from "../api/types";
import { ALICE, dashboardExample, renderApp } from "./fixtures";

const mockFetchMe = vi.mocked(fetchMe);
const mockLogout = vi.mocked(logout);
const mockFetchDashboard = vi.mocked(fetchDashboard);

beforeEach(() => {
  mockFetchMe.mockReset().mockResolvedValue({ ...ALICE });
  vi.mocked(login).mockReset();
  mockLogout.mockReset().mockResolvedValue(undefined);
  mockFetchDashboard.mockReset().mockResolvedValue(dashboardExample());
});

async function renderDashboard(data?: DashboardData) {
  if (data) mockFetchDashboard.mockResolvedValue(data);
  const result = renderApp("/dashboard");
  await screen.findByTestId("welcome-message");
  return result;
}

describe("Dashboard page", () => {
  it("FR1-UI8: profile card shows exact student ID, name and email", async () => {
    await renderDashboard();

    const card = await screen.findByTestId("profile-card");
    expect(card).toHaveTextContent("Student Profile");
    expect(screen.getByTestId("profile-student-id").textContent).toBe("410000001");
    expect(screen.getByTestId("profile-name").textContent).toBe("Alice Chen");
    expect(screen.getByTestId("profile-email").textContent).toBe("410000001@o365.tku.edu.tw");
  });

  it("FR1-UI9: semester card shows name (code), formatted period and 'Open'", async () => {
    await renderDashboard();

    const card = await screen.findByTestId("semester-card");
    expect(card).toHaveTextContent("Current Semester");
    expect(screen.getByTestId("semester-name").textContent).toBe("2026 Fall (115-1)");
    expect(screen.getByTestId("registration-period").textContent).toBe(
      "2026-08-01 00:00 to 2026-12-31 23:59",
    );
    expect(screen.getByTestId("registration-status").textContent).toBe("Open");
  });

  it("FR1-UI9: registrationOpen false shows status 'Closed'", async () => {
    const data = dashboardExample();
    data.currentSemester!.registrationOpen = false;

    await renderDashboard(data);

    expect((await screen.findByTestId("registration-status")).textContent).toBe("Closed");
    expect(screen.getByTestId("registration-period").textContent).toBe(
      "2026-08-01 00:00 to 2026-12-31 23:59",
    );
  });

  it("FR1-UI10: (a) currentSemester null shows 'No current semester has been set.' and no semester-name", async () => {
    const data = dashboardExample();
    data.currentSemester = null;

    await renderDashboard(data);

    expect(await screen.findByTestId("semester-empty")).toHaveTextContent(
      "No current semester has been set.",
    );
    expect(screen.queryByTestId("semester-name")).not.toBeInTheDocument();
  });

  it("FR1-UI10: (b) null period dates show 'Not scheduled' and 'Closed'", async () => {
    const data = dashboardExample();
    data.currentSemester = {
      code: "116-1",
      name: "2027 Fall",
      registrationStartAt: null,
      registrationEndAt: null,
      registrationOpen: false,
    };

    await renderDashboard(data);

    expect((await screen.findByTestId("registration-period")).textContent).toBe("Not scheduled");
    expect(screen.getByTestId("registration-status").textContent).toBe("Closed");
    expect(screen.getByTestId("semester-name").textContent).toBe("2027 Fall (116-1)");
  });

  it("FR1-UI11: app shell shows title, user name and exactly one 'Dashboard' menu item", async () => {
    await renderDashboard();

    expect(screen.getByTestId("app-title")).toHaveTextContent("Tamkang Course Registration System");
    expect(screen.getByTestId("user-name").textContent).toBe("Alice Chen");

    const items = within(screen.getByTestId("side-nav")).getAllByRole("menuitem");
    expect(items).toHaveLength(1);
    expect(items[0].textContent?.trim()).toBe("Dashboard");
  });

  it("FR1-UI12: clicking 'Log out' calls logout once and shows the login card with 'You have been logged out.'", async () => {
    const { user } = await renderDashboard();

    const button = screen.getByTestId("logout-button");
    expect(button).toHaveTextContent("Log out");
    await user.click(button);

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId("logout-message")).toHaveTextContent("You have been logged out.");
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
  });

  it("FR1-UI12: when logout() rejects, the same navigation to the login page happens", async () => {
    mockLogout.mockRejectedValue(new TypeError("Failed to fetch"));
    const { user } = await renderDashboard();

    await user.click(screen.getByTestId("logout-button"));

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId("logout-message")).toHaveTextContent("You have been logged out.");
  });

  it("FR1-UI13: (a) fetchDashboard 401 sends the user to the login card", async () => {
    mockFetchDashboard.mockRejectedValue(new ApiError(401, "Authentication required."));

    renderApp("/dashboard");

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
  });

  it("FR1-UI13: (b) fetchDashboard 500 shows the dashboard error and keeps the app shell", async () => {
    mockFetchDashboard.mockRejectedValue(new ApiError(500, "An unexpected error occurred."));

    renderApp("/dashboard");

    expect(await screen.findByTestId("dashboard-error")).toHaveTextContent(
      "Unable to load your dashboard. Please try again later.",
    );
    expect(screen.getByTestId("app-title")).toBeInTheDocument();
    expect(screen.getByTestId("side-nav")).toBeInTheDocument();
    expect(screen.getByTestId("user-name").textContent).toBe("Alice Chen");
    expect(screen.queryByTestId("login-card")).not.toBeInTheDocument();
  });
});
