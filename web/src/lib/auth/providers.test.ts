import { describe, expect, it, vi } from "vitest";

vi.mock("next/server", () => ({ connection: async () => {} }));

const { firstClientId } = await import("./providers");

describe("firstClientId", () => {
  it("takes the first non-empty ID", () => {
    expect(firstClientId("web.apps, ios.apps")).toBe("web.apps");
    expect(firstClientId(" , web.apps")).toBe("web.apps");
  });

  it("is undefined when nothing is configured", () => {
    expect(firstClientId(undefined)).toBeUndefined();
    expect(firstClientId("")).toBeUndefined();
  });
});
