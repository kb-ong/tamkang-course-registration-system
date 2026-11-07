// FR5-UI9: real api/registrations module with fetch stubbed.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { dropCourse, fetchRegistrations } from "../api/registrations";
import { ApiError } from "../api/client";
import { DROP_RESULT, scheduleFixture } from "./scheduleFixtures";

type FetchInit = { method?: string; credentials?: string; headers?: Record<string, string>; body?: unknown };

function clearXsrfCookie() {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
}
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

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

describe("FR5-UI9: registrations API client", () => {
  it("FR5-UI9: fetchRegistrations() GETs /api/registrations and resolves the JSON", async () => {
    const body = scheduleFixture();
    fetchMock.mockImplementation(async () => jsonResponse(200, body));
    await expect(fetchRegistrations()).resolves.toEqual(body);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/registrations");
    const init = fetchMock.mock.calls[0][1] as FetchInit | undefined;
    expect([undefined, "GET"]).toContain(init?.method);
  });

  it("FR5-UI9: fetchRegistrations('114-2') adds the semester query; an empty string is omitted", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, scheduleFixture()));
    await fetchRegistrations("114-2");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/registrations?semester=114-2");
    await fetchRegistrations("");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/registrations");
  });

  it("FR5-UI9: dropCourse(1) sends DELETE with same-origin credentials, XSRF header and no body", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockImplementation(async () => jsonResponse(200, DROP_RESULT));

    await expect(dropCourse(1)).resolves.toEqual(DROP_RESULT);

    const calls = fetchMock.mock.calls.filter((c) => c[0] === "/api/registrations/1");
    expect(calls).toHaveLength(1);
    const init = calls[0][1] as FetchInit;
    expect(init.method).toBe("DELETE");
    expect(init.credentials).toBe("same-origin");
    expect(init.headers?.["X-XSRF-TOKEN"]).toBe("abc123");
    expect(init.body).toBeUndefined();
  });

  it("FR5-UI9: a 404 with a message body rejects with ApiError(404, message)", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockImplementation(async () => jsonResponse(404, { message: "X" }));
    const err = await dropCourse(1).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.message).toBe("X");
  });
});
