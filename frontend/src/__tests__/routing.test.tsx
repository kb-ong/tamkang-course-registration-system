// FR1 route guard criteria: FR1-UI4, FR1-UI5, FR1-UI7.
import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/auth", () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
}));
vi.mock("../api/dashboard", () => ({
  fetchDashboard: vi.fn(),
}));

vi.mock("../api/registrations", () => ({
  fetchRegistrations: vi.fn(),
  dropCourse: vi.fn(),
}));

import { fetchMe, login, logout } from "../api/auth";
import { fetchDashboard } from "../api/dashboard";
import { fetchRegistrations } from "../api/registrations";
import { emptySchedule } from "./scheduleFixtures";
import { ALICE, dashboardExample, renderApp } from "./fixtures";

const mockFetchMe = vi.mocked(fetchMe);
const mockFetchDashboard = vi.mocked(fetchDashboard);

beforeEach(() => {
  vi.mocked(fetchRegistrations).mockReset().mockResolvedValue(emptySchedule());
  mockFetchMe.mockReset().mockResolvedValue(null);
  vi.mocked(login).mockReset();
  vi.mocked(logout).mockReset().mockResolvedValue(undefined);
  mockFetchDashboard.mockReset().mockResolvedValue(dashboardExample());
});

describe("Routing and route guard", () => {
  it("FR1-UI4: anonymous user at /dashboard sees the login card and fetchDashboard is not called", async () => {
    renderApp("/dashboard");

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
    expect(mockFetchDashboard).not.toHaveBeenCalled();
  });

  it("FR1-UI5: (a) authenticated user at / sees the dashboard welcome", async () => {
    mockFetchMe.mockResolvedValue({ ...ALICE });

    renderApp("/");

    expect(await screen.findByTestId("welcome-message")).toBeInTheDocument();
  });

  it("FR1-UI5: (b) anonymous user at / sees the login card", async () => {
    renderApp("/");

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
  });

  it("FR1-UI5: unknown route /no-such-page sends an authenticated user to the dashboard", async () => {
    mockFetchMe.mockResolvedValue({ ...ALICE });

    renderApp("/no-such-page");

    expect(await screen.findByTestId("welcome-message")).toBeInTheDocument();
  });

  it("FR1-UI5: unknown route /no-such-page sends an anonymous user to the login card", async () => {
    renderApp("/no-such-page");

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
  });

  it("FR1-UI7: while fetchMe is pending, /dashboard shows auth-loading only", async () => {
    mockFetchMe.mockReturnValue(new Promise(() => {}));

    renderApp("/dashboard");

    expect(await screen.findByTestId("auth-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("login-card")).not.toBeInTheDocument();
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
    expect(mockFetchDashboard).not.toHaveBeenCalled();
  });
});
