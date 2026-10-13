import { describe, expect, it } from "vitest";
import { fromLocalInput, isStaff, move, toLocalInput } from "./client";

describe("move", () => {
  it("moves an item earlier or later without touching the input", () => {
    const pages = ["a", "b", "c", "d"];
    expect(move(pages, 2, 0)).toEqual(["c", "a", "b", "d"]);
    expect(move(pages, 0, 3)).toEqual(["b", "c", "d", "a"]);
    expect(pages).toEqual(["a", "b", "c", "d"]);
  });
});

describe("local date inputs", () => {
  it("round-trips an instant through the datetime-local format", () => {
    const iso = "2026-10-13T18:30:00.000Z";
    const local = toLocalInput(iso);
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(fromLocalInput(local)).toBe(iso);
  });

  it("treats empty or invalid input as no time", () => {
    expect(fromLocalInput("")).toBeNull();
    expect(fromLocalInput("not a date")).toBeNull();
  });
});

describe("isStaff", () => {
  it("is true only for admins", () => {
    expect(isStaff(["READER", "ADMIN"])).toBe(true);
    expect(isStaff(["READER"])).toBe(false);
  });
});
