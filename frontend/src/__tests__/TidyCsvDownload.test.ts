// TIDY-4 (CSV guard) and TIDY-5 (download revoke timing).
import { afterEach, describe, expect, it, vi } from "vitest";
import { buildScheduleCsv } from "../utils/scheduleCsv";
import { downloadTextFile } from "../utils/download";
import type { RegisteredCourse } from "../api/types";
import { fixtureCourses } from "./scheduleFixtures";

function row(over: Partial<RegisteredCourse>): RegisteredCourse {
  return { ...fixtureCourses()[0], ...over };
}
const dataLine = (c: RegisteredCourse) => buildScheduleCsv([c], 3).split("\r\n")[1];

describe("TIDY-4: CSV guard", () => {
  it("TIDY-4: semesterCode starting with a trigger gets a leading apostrophe", () => {
    expect(dataLine(row({ semesterCode: "=X" }))).toContain(",'=X,");
    expect(dataLine(row({ semesterCode: "-1" }))).toContain(",'-1,");
  });

  it("TIDY-4: a leading carriage return is guarded, then quoted", () => {
    expect(buildScheduleCsv([row({ name: "\rA" })], 3)).toContain(',"\'\rA",');
  });

  it("TIDY-4: a normal semester code is unchanged", () => {
    expect(dataLine(row({ semesterCode: "115-1" }))).toContain(",115-1,");
  });

  it("TIDY-4: Registered At is never prefixed; credits, header and total row are unguarded", () => {
    const csv = buildScheduleCsv([row({ credits: -2 })], -2).split("\r\n");
    expect(csv[0]).toBe("Course Code,Course Name,Credits,Instructor,Semester,Registered At");
    expect(csv[1].endsWith(",2026-09-01 09:00")).toBe(true);
    expect(csv[1]).not.toContain("'2026");
    expect(csv[1]).toContain(",-2,");
    expect(csv[2]).toBe("Total Credits,,-2,,,");
  });

  it("TIDY-4: every string cell (code, name, instructor, semester) is guarded", () => {
    const line = dataLine(row({ code: "=a", name: "+b", instructor: "@c", semesterCode: "-d" }));
    expect(line.startsWith("'=a,'+b,3,'@c,'-d,")).toBe(true);
  });
});

describe("TIDY-5: download revoke", () => {
  const origCreate = URL.createObjectURL;
  const origRevoke = URL.revokeObjectURL;
  afterEach(() => {
    vi.useRealTimers();
    URL.createObjectURL = origCreate;
    URL.revokeObjectURL = origRevoke;
    vi.restoreAllMocks();
  });

  it("TIDY-5: the link is removed synchronously and the object URL is revoked in a 0 ms timer", () => {
    vi.useFakeTimers();
    const create = vi.fn(() => "blob:x");
    const revoke = vi.fn();
    URL.createObjectURL = create as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = revoke as unknown as typeof URL.revokeObjectURL;
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

    downloadTextFile("a.csv", "x");

    expect(create).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector("a[download]")).toBeNull();
    expect(revoke).not.toHaveBeenCalled();

    vi.runAllTimers();

    expect(revoke).toHaveBeenCalledTimes(1);
    expect(revoke).toHaveBeenCalledWith("blob:x");
  });
});
