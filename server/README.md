# 감정 가드 API 프록시

앱 대신 Claude API를 호출해 주는 작은 서버입니다. API 키를 앱이 아니라 서버에만 두기 위해 만들었습니다.
Cloudflare Workers **무료 플랜**에서 동작합니다. 실제로 돈이 드는 건 Claude API 사용량뿐입니다.

설계 배경은 [../docs/DESIGN.md](../docs/DESIGN.md) §7을 참고하세요.

## 구성

```
src/
├── index.ts      진입점, 라우팅: 인증 → 검증 → 개인정보 가리기 → 할당량 → Claude → 응답
├── auth.ts       Firebase ID 토큰 / App Check 검증 (운영), 개발 키 검증 (dev)
├── validate.ts   요청 본문 검사 (zod): 문장 500자, 맥락 8줄
├── pii.ts        전화번호·이메일·주민번호·카드·계좌 가리기
├── prompt.ts     시스템 프롬프트와 사용자 메시지
├── analyzer.ts   Claude 호출 (공식 SDK, 구조화된 출력) + 개발용 가짜 분석기
├── quota.ts      사용자별 하루/분당 할당량 (Durable Object)
├── http.ts       JSON 응답, 오류 형식
└── types.ts      환경 변수, 카테고리, 위험 구간
test/             vitest 단위 테스트 (54개)
```

## API

### `POST /v1/analyze`

요청 헤더 (운영)
- `Authorization: Bearer <Firebase ID 토큰>`
- `X-Firebase-AppCheck: <App Check 토큰>`

요청 헤더 (dev 모드)
- `X-Dev-Key: <DEV_KEY 값>`
- `X-Install-Id: <앱 설치 ID, 8~64자 영숫자와 하이픈>`

```json
{
  "text": "너 진짜 왜 그래?",
  "context": [{ "speaker": "other", "text": "오늘 회사에서 너무 힘들었어" }],
  "contextConfidence": 0.9,
  "relationship": "FRIEND"
}
```

응답

```json
{
  "score": 48,
  "level": "CAUTION",
  "category": "SARCASM",
  "reason": "상대가 힘든 이야기를 하는 중이라 다그치는 말투로 들릴 수 있어요.",
  "alternative": "무슨 일 있었는지 더 얘기해줄래?",
  "alternatives": [
    { "tone": "SOFT", "text": "무슨 일 있었는지 더 얘기해줄래?" },
    { "tone": "FIRM", "text": "무슨 일인지 제대로 듣고 싶어. 말해줄 수 있어?" },
    { "tone": "PLAYFUL", "text": "누가 우리 OO 힘들게 했어? 다 말해봐!" }
  ],
  "quota": { "limit": 100, "remaining": 87, "resetAt": "2026-11-18T00:00:00+09:00" }
}
```

- `relationship`: `UNKNOWN`(기본값) · `FRIEND` · `WORK` · `PARTNER` · `FAMILY`. 직장이면 더 엄격하게 보고 대안은 존댓말, 가벼운 말투(PLAYFUL)는 뺀다.
- `alternatives`: 말투별 대안, `SOFT` → `FIRM` → `PLAYFUL` 순서로 최대 3개. 안전하면 빈 배열.
- `alternative`: 첫 번째 대안. 구버전 앱 호환용으로 계속 보낸다.

오류는 항상 `{ "error": { "code": "...", "message": "..." } }` 형식입니다.

| 상태 | code | 의미 |
| --- | --- | --- |
| 400 | `INVALID_JSON`, `INVALID_REQUEST` | 본문 형식 오류 |
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·위조 |
| 413 | `BODY_TOO_LARGE` | 본문 16KB 초과 |
| 422 | `ANALYSIS_REFUSED` | 대체 모델까지 분석을 거절 |
| 429 | `DAILY_LIMIT`, `MINUTE_LIMIT` | 할당량 초과 (`Retry-After` 헤더 포함) |
| 5xx | `UPSTREAM_*`, `SERVER_MISCONFIGURED` | Claude 호출 실패 또는 서버 설정 문제 (할당량은 돌려줌) |

### `GET /v1/health`

`{ "ok": true, "mock": false }`

## 로컬에서 실행하기

```bash
npm install
cp .dev.vars.example .dev.vars   # 값 채우기
npm run dev                      # http://localhost:8787
```

`.dev.vars`에 `MOCK_ANALYSIS=true`를 두면 Claude를 부르지 않고 규칙 기반 가짜 결과를 돌려줍니다. 비용 없이 앱 UI를 개발할 때 쓰세요.

Android 에뮬레이터에서 로컬 서버에 접속하려면 주소를 `http://10.0.2.2:8787`로 쓰면 됩니다.

## 테스트

```bash
npm test          # 단위 테스트
npm run typecheck # 타입 검사
```

## 배포하기 (무료)

1. [Cloudflare](https://dash.cloudflare.com/sign-up) 무료 계정을 만듭니다. 신용카드는 필요 없습니다.
2. 로그인합니다.
   ```bash
   npx wrangler login
   ```
3. Claude API 키를 비밀값으로 넣습니다.
   ```bash
   npx wrangler secret put ANTHROPIC_API_KEY
   ```
4. 인증 방식을 고릅니다.
   - **Firebase로 운영**: `wrangler.jsonc`의 `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`를 채웁니다. (Firebase 콘솔 → 프로젝트 설정)
   - **시연용으로 간단히**: `AUTH_MODE`를 `"dev"`로 바꾸고 `npx wrangler secret put DEV_KEY`로 긴 임의 문자열을 넣습니다.
5. 배포합니다.
   ```bash
   npm run deploy
   ```
   `https://emotionguard-proxy.<계정>.workers.dev` 주소가 나옵니다.

## 설정값 (`wrangler.jsonc`의 `vars`)

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `MODEL` | `claude-opus-5-5` | 분석 모델. 비용을 줄이려면 `claude-haiku-5-5` |
| `AUTH_MODE` | `firebase` | `firebase` 또는 `dev` |
| `APP_CHECK_REQUIRED` | `true` | App Check 토큰 필수 여부 |
| `FREE_DAILY_LIMIT` | `100` | 무료 사용자 하루 한도 |
| `PREMIUM_DAILY_LIMIT` | `2000` | 프리미엄(`plan: "premium"` 클레임) 하루 한도 |
| `PER_MINUTE_LIMIT` | `20` | 분당 한도 |
| `MOCK_ANALYSIS` | `false` | `true`면 가짜 분석 (비용 0) |

## 참고

- 메시지 내용은 로그에 남기지 않습니다. 실패할 때도 오류 코드와 글자 수만 기록합니다.
- 안전 분류기가 분석을 거절하면 서버 측 대체 모델(`fallbacks: "default"`)로 한 번 더 시도합니다.
- 할당량 날짜 기준은 한국 시간 자정입니다.
