/**
 * 감정 가드 API 프록시 진입점 (Cloudflare Worker).
 *
 *   GET  /v1/health   서버 상태
 *   POST /v1/analyze  문장 감정 분석
 *
 * 처리 순서: 인증 → 입력 검증 → 개인정보 가리기 → 할당량 차감 → Claude 호출 → 응답
 * Claude 호출이 실패하면 차감한 할당량을 돌려준다.
 *
 * 개인정보 원칙: 요청 본문(메시지 내용)은 어디에도 로그로 남기지 않는다.
 */
import { analyzeMock, analyzeWithClaude } from "./analyzer";
import { authenticate } from "./auth";
import { HttpError, errorResponse, json } from "./http";
import { maskPii } from "./pii";
import { levelOf, type Caller, type Env } from "./types";
import { parseAnalyzeRequest } from "./validate";

export { QuotaCounter } from "./quota";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await route(request, env);
    } catch (err) {
      if (err instanceof HttpError) return errorResponse(err);
      console.error("unhandled", err instanceof Error ? err.name : typeof err);
      return errorResponse(new HttpError(500, "INTERNAL", "서버 내부 오류입니다."));
    }
  },
} satisfies ExportedHandler<Env>;

async function route(request: Request, env: Env): Promise<Response> {
  const { pathname } = new URL(request.url);

  if (pathname === "/v1/health") {
    if (request.method !== "GET") throw methodNotAllowed("GET");
    return json({ ok: true, mock: env.MOCK_ANALYSIS === "true" });
  }

  if (pathname === "/v1/analyze") {
    if (request.method !== "POST") throw methodNotAllowed("POST");
    return analyze(request, env);
  }

  throw new HttpError(404, "NOT_FOUND", "없는 경로입니다.");
}

async function analyze(request: Request, env: Env): Promise<Response> {
  const caller = await authenticate(request, env);
  const body = await parseAnalyzeRequest(request);

  const text = maskPii(body.text);
  const context = body.context.map((line) => ({ ...line, text: maskPii(line.text) }));

  const quota = env.QUOTA.get(env.QUOTA.idFromName(caller.userId));
  const decision = await quota.consume(dailyLimitOf(caller, env), toInt(env.PER_MINUTE_LIMIT, 20));
  const quotaInfo = { limit: decision.limit, remaining: decision.remaining, resetAt: decision.resetAt };

  if (!decision.allowed) {
    const daily = decision.reason === "DAILY_LIMIT";
    throw new HttpError(
      429,
      daily ? "DAILY_LIMIT" : "MINUTE_LIMIT",
      daily ? "오늘 사용할 수 있는 분석 횟수를 모두 썼어요." : "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.",
      { "retry-after": daily ? String(secondsUntil(decision.resetAt)) : "60" },
    );
  }

  let result;
  try {
    result = env.MOCK_ANALYSIS === "true"
        ? analyzeMock(text, body.relationship)
        : await analyzeWithClaude(env, text, context, body.relationship);
  } catch (err) {
    await quota.refund();
    if (err instanceof HttpError) console.warn("analyze_failed", err.code, `len=${text.length}`);
    throw err;
  }

  return json({ ...result, level: levelOf(result.score), quota: quotaInfo });
}

function dailyLimitOf(caller: Caller, env: Env): number {
  return caller.plan === "premium"
    ? toInt(env.PREMIUM_DAILY_LIMIT, 2000)
    : toInt(env.FREE_DAILY_LIMIT, 100);
}

function toInt(value: string | undefined, fallback: number): number {
  const n = Number.parseInt(value ?? "", 10);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

function secondsUntil(iso: string): number {
  return Math.max(1, Math.ceil((Date.parse(iso) - Date.now()) / 1000));
}

function methodNotAllowed(allowed: string): HttpError {
  return new HttpError(405, "METHOD_NOT_ALLOWED", `${allowed}만 허용됩니다.`, { allow: allowed });
}
