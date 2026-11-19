/**
 * 서버 전체에서 쓰는 타입: 환경 변수(Env), 분석 결과, 카테고리, 위험 구간.
 */
import type { QuotaCounter } from "./quota";

export interface Env {
  /** Claude API 키. `wrangler secret put ANTHROPIC_API_KEY`로 넣는다. */
  ANTHROPIC_API_KEY?: string;
  /** 사용할 Claude 모델 ID. */
  MODEL: string;
  /** "firebase"(운영) 또는 "dev"(로컬 개발·시연). */
  AUTH_MODE: string;
  /** dev 모드에서 X-Dev-Key 헤더와 비교할 비밀값. */
  DEV_KEY?: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  /** "true"면 App Check 토큰이 없을 때 거부. */
  APP_CHECK_REQUIRED: string;
  FREE_DAILY_LIMIT: string;
  PREMIUM_DAILY_LIMIT: string;
  PER_MINUTE_LIMIT: string;
  /** "true"면 Claude를 부르지 않고 규칙 기반 가짜 결과를 반환 (개발용). */
  MOCK_ANALYSIS?: string;
  QUOTA: DurableObjectNamespace<QuotaCounter>;
}

export const CATEGORIES = [
  "NONE",
  "PROFANITY",
  "BELITTLING",
  "SARCASM",
  "BLAME",
  "SEXUAL",
  "HATE",
  "THREAT",
] as const;
export type Category = (typeof CATEGORIES)[number];

export type Level = "SAFE" | "CAUTION" | "DANGER";

/** 0~30 안전, 31~60 주의, 61~100 위험 */
export function levelOf(score: number): Level {
  if (score <= 30) return "SAFE";
  if (score <= 60) return "CAUTION";
  return "DANGER";
}

/** 대안 문장의 말투: 부드럽게 · 단호하지만 정중하게 · 가볍게 */
export const TONES = ["SOFT", "FIRM", "PLAYFUL"] as const;
export type Tone = (typeof TONES)[number];

/** 대화 상대와의 관계. UNKNOWN이면 관계를 모르는 것으로 보고 보통 수준으로 판단한다. */
export const RELATIONSHIPS = ["UNKNOWN", "FRIEND", "WORK", "PARTNER", "FAMILY"] as const;
export type Relationship = (typeof RELATIONSHIPS)[number];

export interface Alternative {
  tone: Tone;
  text: string;
}

export interface Analysis {
  score: number;
  category: Category;
  reason: string;
  /** 첫 번째 대안 (구버전 앱 호환용). 안전하면 빈 문자열 */
  alternative: string;
  /** 말투별 대안, 최대 3개. 안전하면 빈 배열 */
  alternatives: Alternative[];
}

export type Plan = "free" | "premium";

export interface Caller {
  /** 할당량을 셀 사용자 ID (Firebase uid 또는 dev:설치ID) */
  userId: string;
  plan: Plan;
}
