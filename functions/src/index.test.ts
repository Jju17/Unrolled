import { describe, expect, it } from "vitest";
import { ping } from "./index";

describe("ping", () => {
  it("answers ok", async () => {
    const request = { data: {}, rawRequest: {} as never, acceptsStreaming: false };
    await expect(ping.run(request)).resolves.toEqual({ ok: true });
  });
});
