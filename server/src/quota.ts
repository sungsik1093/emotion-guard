/**
 * 사용자별 호출 할당량 (Durable Object).
 *
 * 사용자 ID마다 객체가 하나씩 생기고, 한 객체 안의 요청은 순서대로 하나씩 처리되므로
 * 동시에 요청이 몰려도 횟수가 꼬이지 않는다.
 *
 * - 하루 한도: 한국 시간 자정에 초기화
 * - 분당 한도: 1분 단위 고정 창
 * - Claude 호출이 실패하면 refund()로 1회 돌려준다
 */
import { DurableObject } from "cloudflare:workers";

interface Counters {
  day: string;
  dayCount: number;
  minute: number;
  minuteCount: number;
}

export interface QuotaDecision {
  allowed: boolean;
  /** 거절 사유: 하루 한도 / 분당 한도 */
  reason?: "DAILY_LIMIT" | "MINUTE_LIMIT";
  limit: number;
  remaining: number;
  resetAt: string;
}

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/** 한국 시간 기준 날짜 "2026-11-17" */
export function kstDay(nowMs: number): string {
  return new Date(nowMs + KST_OFFSET_MS).toISOString().slice(0, 10);
}

/** 다음 한국 시간 자정 (ISO, +09:00 표기) */
export function nextKstMidnight(nowMs: number): string {
  const kst = new Date(nowMs + KST_OFFSET_MS);
  kst.setUTCHours(24, 0, 0, 0);
  return kst.toISOString().slice(0, 19) + "+09:00";
}

/** 저장소와 무관한 순수 계산. 테스트하기 쉽도록 분리했다. */
export function decide(
  current: Counters | undefined,
  nowMs: number,
  dailyLimit: number,
  minuteLimit: number,
): { next: Counters; decision: QuotaDecision } {
  const day = kstDay(nowMs);
  const minute = Math.floor(nowMs / 60_000);
  const c: Counters =
    current && current.day === day
      ? { ...current }
      : { day, dayCount: 0, minute, minuteCount: 0 };
  if (c.minute !== minute) {
    c.minute = minute;
    c.minuteCount = 0;
  }

  const resetAt = nextKstMidnight(nowMs);
  if (c.dayCount >= dailyLimit) {
    return { next: c, decision: { allowed: false, reason: "DAILY_LIMIT", limit: dailyLimit, remaining: 0, resetAt } };
  }
  if (c.minuteCount >= minuteLimit) {
    return {
      next: c,
      decision: { allowed: false, reason: "MINUTE_LIMIT", limit: dailyLimit, remaining: dailyLimit - c.dayCount, resetAt },
    };
  }

  c.dayCount += 1;
  c.minuteCount += 1;
  return { next: c, decision: { allowed: true, limit: dailyLimit, remaining: dailyLimit - c.dayCount, resetAt } };
}

export class QuotaCounter extends DurableObject {
  async consume(dailyLimit: number, minuteLimit: number): Promise<QuotaDecision> {
    const current = await this.ctx.storage.get<Counters>("c");
    const { next, decision } = decide(current, Date.now(), dailyLimit, minuteLimit);
    await this.ctx.storage.put("c", next);
    return decision;
  }

  async refund(): Promise<void> {
    const c = await this.ctx.storage.get<Counters>("c");
    if (!c || c.day !== kstDay(Date.now())) return;
    c.dayCount = Math.max(0, c.dayCount - 1);
    c.minuteCount = Math.max(0, c.minuteCount - 1);
    await this.ctx.storage.put("c", c);
  }
}
