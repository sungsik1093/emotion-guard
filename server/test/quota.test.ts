import { describe, expect, it } from "vitest";
import { decide, kstDay, nextKstMidnight } from "../src/quota";

// 2026-11-17 14:59:30 UTC = 2026-11-17 23:59:30 KST
const T = Date.UTC(2026, 10, 17, 14, 59, 30);

describe("KST 날짜", () => {
  it("한국 시간 기준으로 날짜와 자정을 계산한다", () => {
    expect(kstDay(T)).toBe("2026-11-17");
    expect(kstDay(T + 60_000)).toBe("2026-11-18");
    expect(nextKstMidnight(T)).toBe("2026-11-18T00:00:00+09:00");
  });
});

describe("decide", () => {
  it("하루 한도까지 허용하고 그다음은 거절한다", () => {
    let c;
    for (let i = 1; i <= 3; i++) {
      const r = decide(c, T + i * 61_000 - 61_000 * 3, 3, 100);
      expect(r.decision).toMatchObject({ allowed: true, remaining: 3 - i });
      c = r.next;
    }
    const r = decide(c, T - 1000, 3, 100);
    expect(r.decision).toMatchObject({ allowed: false, reason: "DAILY_LIMIT", remaining: 0 });
  });

  it("분당 한도를 넘으면 거절하고 다음 분에는 다시 허용한다", () => {
    const start = Date.UTC(2026, 10, 17, 3, 0, 0);
    let c = decide(undefined, start, 100, 2).next;
    c = decide(c, start + 1000, 100, 2).next;
    const blocked = decide(c, start + 2000, 100, 2);
    expect(blocked.decision).toMatchObject({ allowed: false, reason: "MINUTE_LIMIT", remaining: 98 });
    expect(decide(blocked.next, start + 60_000, 100, 2).decision.allowed).toBe(true);
  });

  it("한국 시간 자정이 지나면 초기화된다", () => {
    const full = decide({ day: "2026-11-17", dayCount: 3, minute: 0, minuteCount: 0 }, T, 3, 100);
    expect(full.decision.allowed).toBe(false);
    const nextDay = decide(full.next, T + 60_000, 3, 100);
    expect(nextDay.decision).toMatchObject({ allowed: true, remaining: 2 });
  });
});
