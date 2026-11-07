// FR1 API client criteria (real modules, fetch stubbed): FR1-UI14, FR1-UI15, FR1-UI16.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchMe, login, logout } from "../api/auth";
import { ApiError, apiFetch } from "../api/client";

const ALICE = {
  studentId: "410000001",
  name: "Alice Chen",
  email: "410000001@o365.tku.edu.tw",
};

function clearXsrfCookie() {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

type FetchInit = {
  method?: string;
  credentials?: string;
  headers?: Record<string, string>;
  body?: unknown;
};

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  clearXsrfCookie();
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  clearXsrfCookie();
});

describe("API client", () => {
  it("FR1-UI14: login() POSTs JSON with credentials, Content-Type and X-XSRF-TOKEN from the cookie", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockImplementation(async () => jsonResponse(200, ALICE));

    const result = await login("410000001", "password123");

    expect(result).toEqual(ALICE);
    const loginCalls = fetchMock.mock.calls.filter((c) => c[0] === "/api/auth/login");
    expect(loginCalls).toHaveLength(1);
    const init = loginCalls[0][1] as FetchInit;
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("same-origin");
    expect(init.headers?.["Content-Type"]).toBe("application/json");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("abc123");
    expect(init.body).toBe(JSON.stringify({ studentId: "410000001", password: "password123" }));
  });

  it("FR1-UI15: logout() with no XSRF cookie bootstraps via /api/auth/csrf, then POSTs logout with the fresh token", async () => {
    fetchMock.mockImplementation(async (path: string) => {
      if (path === "/api/auth/csrf") {
        document.cookie = "XSRF-TOKEN=fresh1; path=/";
        return new Response(null, { status: 204 });
      }
      return new Response(null, { status: 204 });
    });

    await expect(logout()).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [firstPath, firstInit] = fetchMock.mock.calls[0] as [string, FetchInit | undefined];
    expect(firstPath).toBe("/api/auth/csrf");
    expect([undefined, "GET"]).toContain(firstInit?.method);

    const [secondPath, secondInit] = fetchMock.mock.calls[1] as [string, FetchInit];
    expect(secondPath).toBe("/api/auth/logout");
    expect(secondInit.method).toBe("POST");
    expect(secondInit.headers?.["X-XSRF-TOKEN"]).toBe("fresh1");
  });

  it("FR1-UI16: (a) a 401 JSON error rejects with ApiError(401, server message)", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockImplementation(async () =>
      jsonResponse(401, { message: "Invalid student ID or password." }),
    );

    const error = await login("410000001", "wrong-password").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect((error as ApiError).message).toBe("Invalid student ID or password.");
  });

  it("FR1-UI16: (b) a non-JSON 502 rejects with ApiError(502, 'Request failed with status 502')", async () => {
    fetchMock.mockImplementation(
      async () =>
        new Response("<html><body>Bad Gateway</body></html>", {
          status: 502,
          headers: { "Content-Type": "text/html" },
        }),
    );

    const error = await apiFetch("/api/dashboard").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(502);
    expect((error as ApiError).message).toBe("Request failed with status 502");
  });

  it("FR1-UI16: (c) fetchMe() resolves null on 401", async () => {
    fetchMock.mockImplementation(async () =>
      jsonResponse(401, { message: "Authentication required." }),
    );

    await expect(fetchMe()).resolves.toBeNull();
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/me");
  });

  it("FR1-UI16: (c) fetchMe() resolves the profile on 200", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, ALICE));

    await expect(fetchMe()).resolves.toEqual(ALICE);
  });
});
