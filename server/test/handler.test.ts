import { SignJWT, exportJWK, generateKeyPair, createLocalJWKSet, type JWK } from "jose";
import { beforeAll, describe, expect, it } from "vitest";
import worker from "../src/index";
import { setKeyResolversForTesting } from "../src/auth";
import { decide } from "../src/quota";
import type { Env } from "../src/types";

/** Durable Object 대신 메모리에 횟수를 들고 있는 가짜 네임스페이스 */
function fakeQuota() {
  const store = new Map<string, ReturnType<typeof decide>["next"]>();
  const refunds: string[] = [];
  const ns = {
    idFromName: (name: string) => name,
    get: (id: string) => ({
      consume: async (daily: number, minute: number) => {
        const { next, decision } = decide(store.get(id), Date.now(), daily, minute);
        store.set(id, next);
        return decision;
      },
      refund: async () => {
        refunds.push(id);
        const c = store.get(id);
        if (c) store.set(id, { ...c, dayCount: c.dayCount - 1, minuteCount: c.minuteCount - 1 });
      },
    }),
  };
  return { ns, store, refunds };
}

function makeEnv(overrides: Partial<Record<keyof Env, string>> = {}) {
  const quota = fakeQuota();
  const env = {
    MODEL: "claude-opus-5-5",
    AUTH_MODE: "dev",
    DEV_KEY: "secret-dev-key",
    FIREBASE_PROJECT_ID: "emotionguard-test",
    FIREBASE_PROJECT_NUMBER: "123456789",
    APP_CHECK_REQUIRED: "true",
    FREE_DAILY_LIMIT: "3",
    PREMIUM_DAILY_LIMIT: "10",
    PER_MINUTE_LIMIT: "100",
    MOCK_ANALYSIS: "true",
    ...overrides,
    QUOTA: quota.ns,
  } as unknown as Env;
  return { env, quota };
}

const DEV_HEADERS = { "x-dev-key": "secret-dev-key", "x-install-id": "install-0001" };

function analyzeReq(body: unknown, headers: Record<string, string> = DEV_HEADERS) {
  return new Request("https://proxy.test/v1/analyze", {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
}

async function call(req: Request, env: Env) {
  const res = await worker.fetch(req, env);
  return { status: res.status, headers: res.headers, body: (await res.json()) as any };
}

describe("라우팅", () => {
  it("health", async () => {
    const { env } = makeEnv();
    const r = await call(new Request("https://proxy.test/v1/health"), env);
    expect(r).toMatchObject({ status: 200, body: { ok: true, mock: true } });
  });

  it("없는 경로와 잘못된 메서드", async () => {
    const { env } = makeEnv();
    expect((await call(new Request("https://proxy.test/nope"), env)).status).toBe(404);
    const r = await call(new Request("https://proxy.test/v1/analyze"), env);
    expect(r.status).toBe(405);
    expect(r.headers.get("allow")).toBe("POST");
  });
});

describe("dev 모드", () => {
  it("올바른 개발 키면 분석 결과와 남은 횟수를 돌려준다", async () => {
    const { env } = makeEnv();
    const r = await call(analyzeReq({ text: "아 진짜 짜증나게 하네" }), env);
    expect(r.status).toBe(200);
    expect(r.body).toMatchObject({ score: 45, level: "CAUTION", category: "SARCASM", quota: { limit: 3, remaining: 2 } });
    expect(r.body.alternative).not.toBe("");
  });

  it("안전한 문장은 대안 문장이 비어 있다", async () => {
    const { env } = makeEnv();
    const r = await call(analyzeReq({ text: "오늘 저녁 뭐 먹을래?" }), env);
    expect(r.body).toMatchObject({ level: "SAFE", category: "NONE", alternative: "" });
  });

  it.each([
    ["키 없음", { "x-install-id": "install-0001" }],
    ["틀린 키", { "x-dev-key": "wrong", "x-install-id": "install-0001" }],
    ["설치 ID 없음", { "x-dev-key": "secret-dev-key" }],
  ])("%s → 401", async (_name, headers) => {
    const { env } = makeEnv();
    const r = await call(analyzeReq({ text: "안녕" }, headers), env);
    expect(r).toMatchObject({ status: 401, body: { error: { code: "UNAUTHORIZED" } } });
  });

  it("하루 한도를 넘으면 429와 retry-after", async () => {
    const { env } = makeEnv();
    for (let i = 0; i < 3; i++) expect((await call(analyzeReq({ text: "안녕" }), env)).status).toBe(200);
    const r = await call(analyzeReq({ text: "안녕" }), env);
    expect(r).toMatchObject({ status: 429, body: { error: { code: "DAILY_LIMIT" } } });
    expect(Number(r.headers.get("retry-after"))).toBeGreaterThan(0);
  });

  it("사용자마다 할당량이 따로 셈해진다", async () => {
    const { env } = makeEnv();
    for (let i = 0; i < 3; i++) await call(analyzeReq({ text: "안녕" }), env);
    const other = await call(analyzeReq({ text: "안녕" }, { ...DEV_HEADERS, "x-install-id": "install-0002" }), env);
    expect(other.status).toBe(200);
  });

  it("Claude 호출이 실패하면 할당량을 돌려준다", async () => {
    const { env, quota } = makeEnv({ MOCK_ANALYSIS: "false" }); // API 키 없음 → 실패
    const r = await call(analyzeReq({ text: "안녕" }), env);
    expect(r).toMatchObject({ status: 500, body: { error: { code: "SERVER_MISCONFIGURED" } } });
    expect(quota.refunds).toHaveLength(1);
    expect(quota.store.get("dev:install-0001")?.dayCount).toBe(0);
  });

  it("인증보다 입력 검증이 먼저 새지 않는다 (인증 실패가 우선)", async () => {
    const { env } = makeEnv();
    const r = await call(analyzeReq({ text: "" }, {}), env);
    expect(r.status).toBe(401);
  });
});

describe("firebase 모드", () => {
  const PROJECT = "emotionguard-test";
  const NUMBER = "123456789";
  let signId: (claims: Record<string, unknown>, opts?: { aud?: string }) => Promise<string>;
  let signAppCheck: (opts?: { aud?: string }) => Promise<string>;

  beforeAll(async () => {
    const idKey = await generateKeyPair("RS256");
    const acKey = await generateKeyPair("RS256");
    const idJwk: JWK = { ...(await exportJWK(idKey.publicKey)), kid: "id1", alg: "RS256" };
    const acJwk: JWK = { ...(await exportJWK(acKey.publicKey)), kid: "ac1", alg: "RS256" };
    setKeyResolversForTesting({
      idToken: createLocalJWKSet({ keys: [idJwk] }),
      appCheck: createLocalJWKSet({ keys: [acJwk] }),
    });

    signId = (claims, opts = {}) =>
      new SignJWT(claims)
        .setProtectedHeader({ alg: "RS256", kid: "id1" })
        .setIssuer(`https://securetoken.google.com/${PROJECT}`)
        .setAudience(opts.aud ?? PROJECT)
        .setSubject("uid-abc")
        .setIssuedAt()
        .setExpirationTime("1h")
        .sign(idKey.privateKey);

    signAppCheck = (opts = {}) =>
      new SignJWT({})
        .setProtectedHeader({ alg: "RS256", kid: "ac1" })
        .setIssuer(`https://firebaseappcheck.googleapis.com/${NUMBER}`)
        .setAudience(opts.aud ?? `projects/${NUMBER}`)
        .setSubject("1:123456789:android:abc")
        .setIssuedAt()
        .setExpirationTime("1h")
        .sign(acKey.privateKey);
  });

  const fbEnv = (o: Partial<Record<keyof Env, string>> = {}) => makeEnv({ AUTH_MODE: "firebase", ...o });

  it("유효한 ID 토큰 + App Check → 200", async () => {
    const { env, quota } = fbEnv();
    const headers = { authorization: `Bearer ${await signId({})}`, "x-firebase-appcheck": await signAppCheck() };
    const r = await call(analyzeReq({ text: "안녕" }, headers), env);
    expect(r.status).toBe(200);
    expect(quota.store.has("fb:uid-abc")).toBe(true);
  });

  it("premium 클레임이면 프리미엄 한도", async () => {
    const { env } = fbEnv();
    const headers = { authorization: `Bearer ${await signId({ plan: "premium" })}`, "x-firebase-appcheck": await signAppCheck() };
    const r = await call(analyzeReq({ text: "안녕" }, headers), env);
    expect(r.body.quota).toMatchObject({ limit: 10, remaining: 9 });
  });

  it("App Check가 필수인데 없으면 401", async () => {
    const { env } = fbEnv();
    const r = await call(analyzeReq({ text: "안녕" }, { authorization: `Bearer ${await signId({})}` }), env);
    expect(r.status).toBe(401);
  });

  it("App Check가 선택이면 없어도 통과", async () => {
    const { env } = fbEnv({ APP_CHECK_REQUIRED: "false" });
    const r = await call(analyzeReq({ text: "안녕" }, { authorization: `Bearer ${await signId({})}` }), env);
    expect(r.status).toBe(200);
  });

  it.each([
    ["다른 프로젝트의 ID 토큰", async () => ({ authorization: `Bearer ${await signId({}, { aud: "other" })}` })],
    ["다른 프로젝트의 App Check", async () => ({
      authorization: `Bearer ${await signId({})}`,
      "x-firebase-appcheck": await signAppCheck({ aud: "projects/999" }),
    })],
    ["위조 토큰", async () => ({ authorization: "Bearer abc.def.ghi" })],
    ["토큰 없음", async () => ({})],
  ])("%s → 401", async (_name, makeHeaders) => {
    const { env } = fbEnv();
    const r = await call(analyzeReq({ text: "안녕" }, (await makeHeaders()) as Record<string, string>), env);
    expect(r.status).toBe(401);
  });
});
