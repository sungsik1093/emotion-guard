/**
 * JSON 응답과 오류 형식을 한곳에서 만든다.
 * 오류 본문은 항상 { error: { code, message } } 모양이다.
 */

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly headers: Record<string, string> = {},
  ) {
    super(message);
  }
}

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...headers,
    },
  });
}

export function errorResponse(err: HttpError): Response {
  return json({ error: { code: err.code, message: err.message } }, err.status, err.headers);
}
