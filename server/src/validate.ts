/**
 * 앱이 보낸 요청 본문을 검사한다.
 * 길이 제한은 비용과 악용을 막기 위한 것이다. (메신저 한 문장이면 500자로 충분)
 */
import { z } from "zod";
import { HttpError } from "./http";
import { RELATIONSHIPS } from "./types";

export const MAX_TEXT_LENGTH = 500;
export const MAX_CONTEXT_LINES = 8;
export const MAX_CONTEXT_LINE_LENGTH = 300;
export const MAX_BODY_BYTES = 16 * 1024;

const ChatLineSchema = z.object({
  speaker: z.enum(["me", "other"]),
  text: z.string().trim().min(1).max(MAX_CONTEXT_LINE_LENGTH),
});

export const AnalyzeRequestSchema = z.object({
  text: z.string().trim().min(1).max(MAX_TEXT_LENGTH),
  context: z.array(ChatLineSchema).max(MAX_CONTEXT_LINES).default([]),
  /** 앱이 판단한 대화 맥락 신뢰도. 0.5 미만이면 서버도 맥락을 쓰지 않는다. */
  contextConfidence: z.number().min(0).max(1).default(1),
  /** 대화 상대와의 관계. 구버전 앱은 보내지 않으므로 기본값 UNKNOWN */
  relationship: z.enum(RELATIONSHIPS).default("UNKNOWN"),
});

export type AnalyzeRequest = z.infer<typeof AnalyzeRequestSchema>;
export type ChatLine = z.infer<typeof ChatLineSchema>;

export async function parseAnalyzeRequest(request: Request): Promise<AnalyzeRequest> {
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (declared > MAX_BODY_BYTES) {
    throw new HttpError(413, "BODY_TOO_LARGE", "요청 본문이 너무 큽니다.");
  }
  const raw = await request.text();
  if (raw.length > MAX_BODY_BYTES) {
    throw new HttpError(413, "BODY_TOO_LARGE", "요청 본문이 너무 큽니다.");
  }

  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    throw new HttpError(400, "INVALID_JSON", "JSON 형식이 아닙니다.");
  }

  const parsed = AnalyzeRequestSchema.safeParse(body);
  if (!parsed.success) {
    const first = parsed.error.issues[0];
    const where = first?.path.join(".") || "body";
    throw new HttpError(400, "INVALID_REQUEST", `${where}: ${first?.message ?? "형식 오류"}`);
  }

  const req = parsed.data;
  if (req.contextConfidence < 0.5) req.context = [];
  return req;
}
