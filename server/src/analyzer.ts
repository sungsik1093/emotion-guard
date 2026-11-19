/**
 * Claude API를 한 번 호출해서 { score, category, reason, alternatives }를 받는다.
 *
 * - 공식 SDK(@anthropic-ai/sdk)의 구조화된 출력(betaZodOutputFormat)으로 JSON 형식을 보장한다.
 * - effort는 low: 타이핑을 따라가야 하므로 속도가 우선이다.
 * - 서버 측 대체 모델(fallbacks: "default")을 켠다. 안전 분류기가 요청을 거절하면
 *   같은 호출 안에서 서버가 정한 대체 모델로 다시 시도한다. 대체를 지원하지 않는 모델이면
 *   원래 모델로만 실행된다.
 */
import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { z } from "zod";
import { HttpError } from "./http";
import { SYSTEM_PROMPT, buildUserMessage } from "./prompt";
import { CATEGORIES, TONES, type Alternative, type Analysis, type Env, type Relationship } from "./types";
import type { ChatLine } from "./validate";

const AnalysisSchema = z.object({
  score: z.number().int().describe("0~100 사이 정수"),
  category: z.enum(CATEGORIES),
  reason: z.string(),
  alternatives: z
    .array(z.object({ tone: z.enum(TONES), text: z.string() }))
    .describe("말투별 대안 문장. 점수 30 이하이면 빈 배열"),
});

const MAX_ALTERNATIVES = 3;

/** 모델 출력 정리: 빈 문장·중복 말투 제거, 정해진 말투 순서로, 최대 3개 */
export function normalizeAlternatives(score: number, raw: Alternative[]): Alternative[] {
  if (score <= 30) return [];
  const seen = new Set<string>();
  return raw
    .map((a) => ({ tone: a.tone, text: a.text.trim() }))
    .filter((a) => a.text.length > 0 && !seen.has(a.tone) && seen.add(a.tone))
    .sort((a, b) => TONES.indexOf(a.tone) - TONES.indexOf(b.tone))
    .slice(0, MAX_ALTERNATIVES);
}

const REQUEST_TIMEOUT_MS = 15_000;

export async function analyzeWithClaude(
  env: Env,
  text: string,
  context: ChatLine[],
  relationship: Relationship = "UNKNOWN",
): Promise<Analysis> {
  if (!env.ANTHROPIC_API_KEY) {
    throw new HttpError(500, "SERVER_MISCONFIGURED", "서버에 API 키가 설정되지 않았습니다.");
  }

  const client = new Anthropic({
    apiKey: env.ANTHROPIC_API_KEY,
    maxRetries: 1,
    timeout: REQUEST_TIMEOUT_MS,
  });

  let message;
  try {
    message = await client.beta.messages.parse({
      model: env.MODEL,
      max_tokens: 2048,
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
      output_config: { effort: "low", format: betaZodOutputFormat(AnalysisSchema) },
      system: SYSTEM_PROMPT,
      messages: [{ role: "user", content: buildUserMessage(text, context, relationship) }],
    });
  } catch (error) {
    throw toHttpError(error);
  }

  if (message.stop_reason === "refusal") {
    throw new HttpError(422, "ANALYSIS_REFUSED", "이 문장은 분석할 수 없습니다.");
  }
  if (message.stop_reason === "max_tokens") {
    throw new HttpError(502, "UPSTREAM_TRUNCATED", "분석 결과가 잘렸습니다.");
  }

  const out = message.parsed_output;
  if (!out) {
    throw new HttpError(502, "UPSTREAM_BAD_OUTPUT", "분석 결과를 읽지 못했습니다.");
  }

  const score = Math.min(100, Math.max(0, Math.round(out.score)));
  const alternatives = normalizeAlternatives(score, out.alternatives);
  return {
    score,
    category: score <= 30 ? "NONE" : out.category,
    reason: out.reason.trim(),
    alternative: alternatives[0]?.text ?? "",
    alternatives,
  };
}

function toHttpError(error: unknown): HttpError {
  if (error instanceof Anthropic.AuthenticationError || error instanceof Anthropic.PermissionDeniedError) {
    return new HttpError(500, "SERVER_MISCONFIGURED", "서버의 API 키가 올바르지 않습니다.");
  }
  if (error instanceof Anthropic.RateLimitError) {
    return new HttpError(503, "UPSTREAM_BUSY", "잠시 후 다시 시도해 주세요.", { "retry-after": "5" });
  }
  if (error instanceof Anthropic.BadRequestError) {
    return new HttpError(502, "UPSTREAM_REJECTED", "분석 요청이 거부되었습니다.");
  }
  if (error instanceof Anthropic.APIConnectionTimeoutError) {
    return new HttpError(504, "UPSTREAM_TIMEOUT", "분석 시간이 초과되었습니다.");
  }
  if (error instanceof Anthropic.APIError) {
    return new HttpError(502, "UPSTREAM_ERROR", "분석 서버 오류입니다.");
  }
  return new HttpError(502, "UPSTREAM_ERROR", "분석 중 알 수 없는 오류가 발생했습니다.");
}

/**
 * MOCK_ANALYSIS=true일 때 쓰는 가짜 분석기. Claude를 부르지 않으므로 비용이 0이다.
 * 앱 UI·캐릭터 상태 전환을 개발할 때 쓴다. 판단 품질은 기대하지 말 것.
 */
const MOCK_RULES: ReadonlyArray<{ pattern: RegExp; score: number; category: Analysis["category"] }> = [
  { pattern: /죽(여|일|어라)|가만 안 ?둬/, score: 90, category: "THREAT" },
  { pattern: /시[0-9]*발|ㅅㅂ|씨발|병신|ㅂㅅ|개새/, score: 85, category: "PROFANITY" },
  { pattern: /멍청|한심|찐따|수준 ?하고는/, score: 72, category: "BELITTLING" },
  { pattern: /너 ?때문|네 ?탓|니가 ?그렇지/, score: 55, category: "BLAME" },
  { pattern: /어련하시겠|됐거든|아 ?진짜|짜증/, score: 45, category: "SARCASM" },
  // 에뮬레이터에서 adb로는 한글을 입력할 수 없어서 영어 표현도 몇 개 둔다
  { pattern: /\b(fuck|shit)/i, score: 85, category: "PROFANITY" },
  { pattern: /\b(stupid|idiot|loser)\b/i, score: 72, category: "BELITTLING" },
  { pattern: /\b(shut up|whatever)\b/i, score: 45, category: "SARCASM" },
];

export function analyzeMock(text: string, relationship: Relationship = "UNKNOWN"): Analysis {
  for (const rule of MOCK_RULES) {
    if (rule.pattern.test(text)) {
      // 관계 효과를 시연할 수 있게: 직장이면 10점 더 엄격하고, 가벼운 말투 대안은 뺀다
      const work = relationship === "WORK";
      const score = Math.min(100, rule.score + (work ? 10 : 0));
      const all: Alternative[] = [
        { tone: "SOFT", text: "(테스트 모드) 조금 더 부드럽게 말해보는 건 어때요?" },
        { tone: "FIRM", text: "(테스트 모드) 이 부분은 꼭 고쳐줬으면 좋겠어요." },
        { tone: "PLAYFUL", text: "(테스트 모드) 앗, 이건 살짝 서운한데? ㅎㅎ" },
      ];
      const alternatives = all.filter((a) => !(work && a.tone === "PLAYFUL"));
      return {
        score,
        category: rule.category,
        reason: `(테스트 모드) 상대가 상처받을 수 있는 표현이 있어요.${work ? " 직장 대화라 더 엄격하게 봤어요." : ""}`,
        alternative: alternatives[0]!.text,
        alternatives,
      };
    }
  }
  return { score: 10, category: "NONE", reason: "(테스트 모드) 괜찮은 문장이에요.", alternative: "", alternatives: [] };
}