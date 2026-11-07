// FR1 login page criteria: FR1-AC2c, FR1-EX1, FR1-UI1, FR1-UI2, FR1-UI3, FR1-UI6, FR1-UI17.
import { screen, waitFor } from "@testing-library/react";
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
import { ALICE, dashboardExample, renderApp } from "./fixtures";

const mockFetchMe = vi.mocked(fetchMe);
const mockLogin = vi.mocked(login);
const mockLogout = vi.mocked(logout);
const mockFetchDashboard = vi.mocked(fetchDashboard);

beforeEach(() => {
  mockFetchMe.mockReset().mockResolvedValue(null);
  mockLogin.mockReset();
  mockLogout.mockReset().mockResolvedValue(undefined);
  mockFetchDashboard.mockReset().mockResolvedValue(dashboardExample());
});

async function submitCredentials(studentId: string, password: string) {
  const { user } = renderApp("/login");
  await screen.findByTestId("login-card");
  await user.type(screen.getByTestId("login-student-id"), studentId);
  await user.type(screen.getByTestId("login-password"), password);
  await user.click(screen.getByTestId("login-submit"));
  return user;
}

describe("Login page", () => {
  it("FR1-UI1: renders brand, 'Student Login' card, labelled Student ID / Password inputs and 'Log in' button", async () => {
    renderApp("/login");

    const card = await screen.findByTestId("login-card");
    expect(screen.getByTestId("login-brand")).toHaveTextContent("Tamkang Course Registration System");
    expect(card).toHaveTextContent("Student Login");

    const studentIdInput = screen.getByTestId("login-student-id");
    const passwordInput = screen.getByTestId("login-password");
    expect(studentIdInput.tagName).toBe("INPUT");
    expect(passwordInput.tagName).toBe("INPUT");
    expect(screen.getByLabelText("Student ID")).toBe(studentIdInput);
    expect(screen.getByLabelText("Password")).toBe(passwordInput);
    expect(passwordInput).toHaveAttribute("type", "password");

    const submit = screen.getByTestId("login-submit");
    expect(submit.tagName).toBe("BUTTON");
    expect(submit).toHaveAttribute("type", "submit");
    expect(submit).toHaveTextContent("Log in");
  });

  it("FR1-EX1: /login with no router state shows the card and neither login-error nor logout-message", async () => {
    renderApp("/login");

    expect(await screen.findByTestId("login-card")).toBeInTheDocument();
    expect(screen.queryByTestId("login-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("logout-message")).not.toBeInTheDocument();
  });

  it("FR1-UI2: valid credentials call login('410000001','password123') once and show the dashboard welcome", async () => {
    mockLogin.mockResolvedValue({ ...ALICE });

    await submitCredentials("410000001", "password123");

    const welcome = await screen.findByTestId("welcome-message");
    expect(welcome.textContent).toBe("Welcome, Alice Chen");
    expect(mockLogin).toHaveBeenCalledTimes(1);
    expect(mockLogin).toHaveBeenCalledWith("410000001", "password123");
    expect(screen.queryByTestId("login-card")).not.toBeInTheDocument();
  });

  it("FR1-AC2c: 401 from login shows 'Invalid student ID or password.' and stays on the login page", async () => {
    mockLogin.mockRejectedValue(new ApiError(401, "Invalid student ID or password."));

    await submitCredentials("410000001", "wrong-password");

    const error = await screen.findByTestId("login-error");
    expect(error).toHaveTextContent("Invalid student ID or password.");
    expect(screen.getByTestId("login-card")).toBeInTheDocument();
    expect(screen.queryByTestId("welcome-message")).not.toBeInTheDocument();
    expect(mockLogin).toHaveBeenCalledWith("410000001", "wrong-password");
  });

  it("FR1-UI3: empty submit shows both required messages and does not call login", async () => {
    const { user } = renderApp("/login");
    await screen.findByTestId("login-card");

    await user.click(screen.getByTestId("login-submit"));

    expect(await screen.findByText("Please enter your student ID.")).toBeInTheDocument();
    expect(await screen.findByText("Please enter your password.")).toBeInTheDocument();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it("FR1-UI3: whitespace-only student ID shows 'Please enter your student ID.' and does not call login", async () => {
    await submitCredentials("   ", "password123");

    expect(await screen.findByText("Please enter your student ID.")).toBeInTheDocument();
    expect(screen.queryByText("Please enter your password.")).not.toBeInTheDocument();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it("FR1-UI6: an authenticated user opening /login is sent to the dashboard", async () => {
    mockFetchMe.mockResolvedValue({ ...ALICE });

    renderApp("/login");

    expect(await screen.findByTestId("welcome-message")).toBeInTheDocument();
    expect(screen.queryByTestId("login-card")).not.toBeInTheDocument();
  });

  it("FR1-UI17: a 500 ApiError from login shows 'Unable to sign in. Please try again later.'", async () => {
    mockLogin.mockRejectedValue(new ApiError(500, "An unexpected error occurred."));

    await submitCredentials("410000001", "password123");

    const error = await screen.findByTestId("login-error");
    expect(error).toHaveTextContent("Unable to sign in. Please try again later.");
    expect(error).not.toHaveTextContent("An unexpected error occurred.");
    expect(screen.getByTestId("login-card")).toBeInTheDocument();
  });

  it("FR1-UI17: a network TypeError from login shows 'Unable to sign in. Please try again later.'", async () => {
    mockLogin.mockRejectedValue(new TypeError("Failed to fetch"));

    await submitCredentials("410000001", "password123");

    const error = await screen.findByTestId("login-error");
    expect(error).toHaveTextContent("Unable to sign in. Please try again later.");
    expect(error).not.toHaveTextContent("Failed to fetch");
    await waitFor(() => expect(screen.getByTestId("login-card")).toBeInTheDocument());
  });
});
