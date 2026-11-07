// FR2-UI8 and FR3-UI8: real api/courses module with fetch stubbed.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchCourses, fetchSemesters, registerForCourse } from "../api/courses";
import { ApiError } from "../api/client";

function clearXsrfCookie() {
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
}
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
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

const LISTING = { semester: { code: "115-1", name: "2026 Fall", current: true }, courses: [] };

describe("FR2-UI8: courses API client", () => {
  it("FR2-UI8: fetchCourses({}) calls /api/courses and resolves the JSON", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, LISTING));
    await expect(fetchCourses({})).resolves.toEqual(LISTING);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/courses");
  });

  it("FR2-UI8: semester and q appear in that order", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, LISTING));
    await fetchCourses({ semester: "114-2", q: "cs" });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/courses?semester=114-2&q=cs");
  });

  it("FR2-UI8: q is URL-encoded", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, LISTING));
    await fetchCourses({ q: "a b" });
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/^\/api\/courses\?q=a(\+|%20)b$/);
  });

  it("FR2-UI8: empty or undefined values are omitted", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, LISTING));
    await fetchCourses({ semester: "", q: "" });
    await fetchCourses({ semester: undefined, q: undefined });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/courses");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/courses");
  });

  it("FR2-UI8: fetchSemesters calls /api/semesters and resolves the JSON", async () => {
    const body = [
      { code: "115-1", name: "2026 Fall", current: true },
      { code: "114-2", name: "2026 Spring", current: false },
    ];
    fetchMock.mockResolvedValue(jsonResponse(200, body));
    await expect(fetchSemesters()).resolves.toEqual(body);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/semesters");
  });
});

describe("FR3-UI8: registerForCourse", () => {
  it("FR3-UI8: POSTs JSON with CSRF header and resolves the 201 body", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    const body = {
      message: "Successfully registered for CS101.",
      registrationId: 10,
      courseId: 1,
      courseCode: "CS101",
      availableSeats: 49,
    };
    fetchMock.mockResolvedValue(jsonResponse(201, body));

    await expect(registerForCourse(1)).resolves.toEqual(body);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/registrations");
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("same-origin");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("abc123");
    expect(init.body).toBe(JSON.stringify({ courseId: 1 }));
  });

  it("FR3-UI8: a 409 rejects with ApiError(409, message)", async () => {
    document.cookie = "XSRF-TOKEN=abc123; path=/";
    fetchMock.mockResolvedValue(jsonResponse(409, { message: "X" }));

    const err = await registerForCourse(1).then(
      () => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(409);
    expect((err as ApiError).message).toBe("X");
  });
});
