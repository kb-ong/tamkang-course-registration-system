// FR5-UI7: pure CSV builder.
import { describe, expect, it } from "vitest";
import { buildScheduleCsv, scheduleCsvFileName } from "../utils/scheduleCsv";
import type { RegisteredCourse } from "../api/types";
import { fixtureCourses } from "./scheduleFixtures";

const HEADER = "Course Code,Course Name,Credits,Instructor,Semester,Registered At";

function row(over: Partial<RegisteredCourse>): RegisteredCourse {
  return { ...fixtureCourses()[0], ...over };
}

describe("FR5-UI7: buildScheduleCsv", () => {
  it("FR5-UI7: (a) fixture gives header, rows and total row joined by CRLF with no trailing newline", () => {
    const csv = buildScheduleCsv(fixtureCourses(), 6);
    expect(csv).toBe(
      [
        HEADER,
        "CS101,Introduction to Programming,3,Prof. Lee,115-1,2026-09-01 09:00",
        "CS201,Data Structures,3,Prof. Chang,115-1,2026-09-02 10:30",
        "Total Credits,,6,,,",
      ].join("\r\n"),
    );
    expect(csv.endsWith("\n")).toBe(false);
  });

  it("FR5-UI7: (b) comma, quote, CR and LF fields are quoted with embedded quotes doubled", () => {
    const lines = (name: string) => buildScheduleCsv([row({ name })], 3).split("\r\n");
    expect(lines('Algebra, Part "I"')[1]).toContain(',"Algebra, Part ""I""",');
    expect(buildScheduleCsv([row({ name: "A\nB" })], 3)).toContain(',"A\nB",');
    expect(buildScheduleCsv([row({ name: "A\rB" })], 3)).toContain(',"A\rB",');
    expect(buildScheduleCsv([row({ instructor: "Lee, Prof." })], 3)).toContain(',"Lee, Prof.",');
  });

  it("FR5-UI7: (c) text fields starting with = + - @ or tab get a leading apostrophe; numbers are not prefixed", () => {
    expect(buildScheduleCsv([row({ code: "=SUM(1)" })], 3).split("\r\n")[1].startsWith("'=SUM(1),")).toBe(true);
    expect(buildScheduleCsv([row({ name: "+1" })], 3).split("\r\n")[1]).toContain(",'+1,");
    expect(buildScheduleCsv([row({ instructor: "-x" })], 3).split("\r\n")[1]).toContain(",'-x,");
    expect(buildScheduleCsv([row({ instructor: "@home" })], 3).split("\r\n")[1]).toContain(",'@home,");
    expect(buildScheduleCsv([row({ name: "\tTab" })], 3).split("\r\n")[1]).toContain(",'\tTab,");
    // prefix then quoting: a formula containing a comma
    expect(buildScheduleCsv([row({ name: "=A1,B1" })], 3).split("\r\n")[1]).toContain(`,"'=A1,B1",`);
    // numbers never prefixed, even negative
    const line = buildScheduleCsv([row({ credits: -1 })], -1).split("\r\n");
    expect(line[1]).toContain(",-1,");
    expect(line[2]).toBe("Total Credits,,-1,,,");
  });

  it("FR5-UI7: (d) no courses gives header and zero total row only", () => {
    expect(buildScheduleCsv([], 0)).toBe(`${HEADER}\r\nTotal Credits,,0,,,`);
  });

  it("FR5-UI7: (e) file name is registered-courses-{code}.csv", () => {
    expect(scheduleCsvFileName("115-1")).toBe("registered-courses-115-1.csv");
    expect(scheduleCsvFileName("114-2")).toBe("registered-courses-114-2.csv");
  });

  it("FR5-UI7: (f) registeredAt is formatted YYYY-MM-DD HH:mm", () => {
    const csv = buildScheduleCsv([row({ registeredAt: "2026-12-31T23:59:59" })], 3);
    expect(csv.split("\r\n")[1].endsWith(",2026-12-31 23:59")).toBe(true);
  });
});
