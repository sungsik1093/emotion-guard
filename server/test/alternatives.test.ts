import { describe, expect, it } from "vitest";
import { analyzeMock, normalizeAlternatives } from "../src/analyzer";

describe("normalizeAlternatives", () => {
  it("안전한 문장은 대안이 없다", () => {
    expect(normalizeAlternatives(20, [{ tone: "SOFT", text: "x" }])).toEqual([]);
  });

  it("빈 문장과 중복 말투를 지우고 SOFT·FIRM·PLAYFUL 순서로 정렬한다", () => {
    const out = normalizeAlternatives(70, [
      { tone: "PLAYFUL", text: " 가볍게 " },
      { tone: "SOFT", text: "부드럽게" },
      { tone: "SOFT", text: "또 부드럽게" },
      { tone: "FIRM", text: "   " },
    ]);
    expect(out).toEqual([
      { tone: "SOFT", text: "부드럽게" },
      { tone: "PLAYFUL", text: "가볍게" },
    ]);
  });
});

describe("analyzeMock 관계 효과", () => {
  it("직장이면 더 엄격하고 가벼운 말투 대안을 뺀다", () => {
    const friend = analyzeMock("아 진짜 짜증나", "FRIEND");
    const work = analyzeMock("아 진짜 짜증나", "WORK");
    expect(work.score).toBe(friend.score + 10);
    expect(friend.alternatives.map((a) => a.tone)).toEqual(["SOFT", "FIRM", "PLAYFUL"]);
    expect(work.alternatives.map((a) => a.tone)).toEqual(["SOFT", "FIRM"]);
    expect(work.alternative).toBe(work.alternatives[0]!.text);
  });
});
