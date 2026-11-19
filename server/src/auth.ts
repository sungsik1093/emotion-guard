/**
 * 요청한 사람이 누구인지, 진짜 우리 앱인지 확인한다.
 *
 * firebase 모드 (운영)
 *   - Authorization: Bearer <Firebase ID 토큰>  → 익명 계정 uid로 사용자 구분
 *     ID 토큰의 커스텀 클레임 plan === "premium"이면 프리미엄
 *   - X-Firebase-AppCheck: <App Check 토큰>     → Play Integrity로 증명된 정품 앱인지
 *   두 토큰 모두 Google 공개키로 서명을 검증한다 (jose). 공개키는 Worker 안에서 캐시된다.
 *
 * dev 모드 (로컬 개발·시연)
 *   - X-Dev-Key: 서버 비밀값 DEV_KEY와 일치해야 함
 *   - X-Install-Id: 앱 설치마다 만든 ID로 사용자 구분
 */
import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";
import { HttpError } from "./http";
import type { Caller, Env } from "./types";

const FIREBASE_ID_JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const APP_CHECK_JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";

let idTokenKeys: JWTVerifyGetKey = createRemoteJWKSet(new URL(FIREBASE_ID_JWKS_URL));
let appCheckKeys: JWTVerifyGetKey = createRemoteJWKSet(new URL(APP_CHECK_JWKS_URL));

/** 테스트에서 Google 공개키 대신 직접 만든 키를 쓰기 위한 진입점. */
export function setKeyResolversForTesting(keys: { idToken?: JWTVerifyGetKey; appCheck?: JWTVerifyGetKey }) {
  if (keys.idToken) idTokenKeys = keys.idToken;
  if (keys.appCheck) appCheckKeys = keys.appCheck;
}

const unauthorized = (message: string) => new HttpError(401, "UNAUTHORIZED", message);

export async function authenticate(request: Request, env: Env): Promise<Caller> {
  if (env.AUTH_MODE === "dev") return authenticateDev(request, env);
  if (env.AUTH_MODE === "firebase") return authenticateFirebase(request, env);
  throw new HttpError(500, "SERVER_MISCONFIGURED", "AUTH_MODE 설정이 올바르지 않습니다.");
}

async function authenticateFirebase(request: Request, env: Env): Promise<Caller> {
  if (!env.FIREBASE_PROJECT_ID || !env.FIREBASE_PROJECT_NUMBER) {
    throw new HttpError(500, "SERVER_MISCONFIGURED", "Firebase 프로젝트 설정이 비어 있습니다.");
  }

  const auth = request.headers.get("authorization") ?? "";
  const idToken = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!idToken) throw unauthorized("인증 토큰이 없습니다.");

  let uid: string;
  let plan: Caller["plan"] = "free";
  try {
    const { payload } = await jwtVerify(idToken, idTokenKeys, {
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      audience: env.FIREBASE_PROJECT_ID,
      algorithms: ["RS256"],
    });
    if (typeof payload.sub !== "string" || payload.sub.length === 0) throw new Error("no sub");
    uid = payload.sub;
    if (payload.plan === "premium") plan = "premium";
  } catch {
    throw unauthorized("인증 토큰이 유효하지 않습니다.");
  }

  const appCheckToken = request.headers.get("x-firebase-appcheck")?.trim() ?? "";
  if (appCheckToken) {
    try {
      await jwtVerify(appCheckToken, appCheckKeys, {
        issuer: `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
        audience: `projects/${env.FIREBASE_PROJECT_NUMBER}`,
        algorithms: ["RS256"],
      });
    } catch {
      throw unauthorized("앱 인증(App Check)에 실패했습니다.");
    }
  } else if (env.APP_CHECK_REQUIRED === "true") {
    throw unauthorized("앱 인증(App Check) 토큰이 없습니다.");
  }

  return { userId: `fb:${uid}`, plan };
}

const INSTALL_ID_PATTERN = /^[A-Za-z0-9-]{8,64}$/;

async function authenticateDev(request: Request, env: Env): Promise<Caller> {
  if (!env.DEV_KEY) {
    throw new HttpError(500, "SERVER_MISCONFIGURED", "dev 모드인데 DEV_KEY가 설정되지 않았습니다.");
  }
  const key = request.headers.get("x-dev-key") ?? "";
  if (!(await constantTimeEqual(key, env.DEV_KEY))) throw unauthorized("개발 키가 올바르지 않습니다.");

  const installId = request.headers.get("x-install-id") ?? "";
  if (!INSTALL_ID_PATTERN.test(installId)) throw unauthorized("X-Install-Id 헤더가 올바르지 않습니다.");

  return { userId: `dev:${installId}`, plan: "free" };
}

/** 길이와 내용에 따라 비교 시간이 달라지지 않도록 해시끼리 비교한다. */
async function constantTimeEqual(a: string, b: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [ha, hb] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b)),
  ]);
  const va = new Uint8Array(ha);
  const vb = new Uint8Array(hb);
  let diff = 0;
  for (let i = 0; i < va.length; i++) diff |= va[i]! ^ vb[i]!;
  return diff === 0;
}
