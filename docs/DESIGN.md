# 감정 가드 (EmotionGuard) 설계 문서 v2

> 상태: v2 구현 완료 (android/, server/) · 작성일 2026-11-17
> 범위: v1(포트폴리오 시연용)을 **상업 출시가 가능한 수준**으로 끌어올리기 위한 제품·기술 설계

---

## 1. 한 줄 요약

메신저에 메시지를 쓰는 동안 화면 구석의 작은 캐릭터가 문장의 감정 온도에 따라 표정과 색을 바꾸고, 상처가 될 수 있는 말이면 **보내기 전에** 더 나은 표현을 제안하는 안드로이드 앱.

## 2. v2 목표

| 목표 | 측정 기준 |
| --- | --- |
| 켜고 끄기가 VPN처럼 직관적일 것 | 첫 실행부터 보호 켜기까지 1분 이내, 이후 켜기/끄기는 탭 1번 |
| 경고가 아니라 "옆에 있는 친구"처럼 느껴질 것 | 떠 있는 캐릭터의 표정 변화로 상태 전달, 칭찬 상태 포함 |
| 사용자가 캐릭터를 직접 꾸밀 수 있을 것 | 상태별 사진 교체, 언제든 기본값으로 되돌리기 |
| API 키가 앱에 없을 것 | 모든 AI 호출은 프록시 서버 경유, 앱은 Firebase 인증 토큰만 가짐 |
| 개인정보를 최소한으로 보낼 것 | 기기 안 1차 필터 + 개인정보 가리기, 원문 저장 기본 꺼짐 |
| 운영 가능한 구조일 것 | 사용자별 호출 제한, 무료/프리미엄 구분, 크래시 리포트, CI |

## 3. v1 한계와 v2 해결 방법

| v1 한계 | v2 해결 | 관련 절 |
| --- | --- | --- |
| API 키가 앱 안에 있음 | Cloudflare Workers 프록시 + Firebase 익명 인증 + App Check | §7 |
| DB 구조가 바뀌면 기록 삭제 | 파괴적 마이그레이션 제거 + 스키마 export + 마이그레이션 정책 | §8 |
| "전송" 판단이 추정 | 전송 버튼 클릭 + 입력창 비워짐 + 새 말풍선, 세 신호 조합 + `sendConfidence` 기록 | §5.4 |
| 대화 맥락을 화면 위치로만 추정 | 메신저별 `MessengerProfile`(뷰 ID 기반) → 위치 추정은 대비책, 노이즈 필터, 신뢰도 점수 | §5.3 |
| 메신저마다 입력창 구조가 다름 | 패키지별 어댑터 + 앱 안 진단 화면 | §5.3 |
| 입력 텍스트가 그대로 외부로 전송 | 1차 필터(무난한 문장은 호출 안 함) + 개인정보 가리기(앱 + 서버 이중) | §9 |
| ViewBinding 기반 UI | Jetpack Compose 전면 전환 (오버레이 포함) | §5 |
| Play 심사 리스크 | 명시적 고지 화면 + 감지 계층을 인터페이스로 분리해 IME 방식으로 전환 가능하게 | §11 |

---

## 4. 사용자 경험

### 4.1 화면 흐름

```
첫 실행
 └─ 온보딩 (3장)
     1. 무엇을 하는 앱인지
     2. 어떤 데이터가 어디로 가는지 (명시적 고지 + 동의)
     3. 접근성 권한 켜기 안내 → 시스템 설정으로 이동 → 돌아오면 자동 확인
 └─ 홈
     ├─ [큰 원형 토글] 보호 중 / 꺼짐
     ├─ 오늘의 요약 (분석 횟수, 바꿔 보낸 횟수, 평균 온도)
     ├─ 남은 무료 분석 횟수
     └─ 하단 탭: 홈 · 기록 · 캐릭터 · 설정
```

### 4.2 켜기/끄기 (VPN 방식)

안드로이드는 앱이 접근성 서비스를 직접 켜는 것을 허용하지 않는다. 그래서 두 단계로 나눈다.

| 단계 | 누가 | 언제 | 의미 |
| --- | --- | --- | --- |
| 접근성 서비스 활성화 | 사용자 (시스템 설정) | 최초 1회 | 서비스가 살아 있음 |
| 보호 켜기/끄기 | 사용자 (앱 토글) | 언제든 | 서비스 안에서 감지·분석·오버레이를 켜거나 멈춤 |

토글 상태는 DataStore의 `guardEnabled`에 저장하고, 서비스는 이 값을 `Flow`로 구독한다. 꺼져 있으면 이벤트를 받아도 즉시 무시하므로 배터리와 비용이 들지 않는다.

켜고 끄는 입구는 세 곳이다.
1. **홈 화면 토글**
2. **빠른 설정 타일** (`GuardTileService`): 상단바를 내려서 바로 켜고 끔. VPN과 같은 감각.
3. **상태 알림**: 켜져 있는 동안 "감정 가드 보호 중" 알림 + [끄기] 버튼

> 접근성 서비스가 시스템에 의해 꺼지면(앱 업데이트, 사용자가 설정에서 끔) 홈 토글은 "권한 필요" 상태로 바뀌고 다시 켜는 방법을 안내한다.

### 4.3 떠 있는 캐릭터

- 대상 메신저의 입력창에 포커스가 가면 화면 가장자리에 **지름 56dp** 캐릭터가 나타난다.
- 드래그로 옮길 수 있고, 놓으면 가까운 가장자리에 붙는다. 위치는 메신저별로 기억한다.
- 3초간 변화가 없으면 반투명(alpha 0.5)이 된다.
- 메신저를 벗어나면 사라진다. (설정에서 "항상 표시" 선택 가능)
- **탭하면** 경고 카드가 펼쳐진다: 점수, 유형, 이유, 대안 문장, [대안으로 바꾸기] [무시].
- 위험(61점 이상)이 되면 카드를 자동으로 펼칠지 설정할 수 있다 (기본: 캐릭터만 흔들고 펼치지 않음).
- 오버레이는 `FLAG_NOT_FOCUSABLE`이라 떠 있는 동안에도 계속 타이핑할 수 있다.

### 4.4 캐릭터 상태

| 상태 (`MoodState`) | 조건 | 기본 표정 | 테두리 색 | 동작 |
| --- | --- | --- | --- | --- |
| `IDLE` | 입력 없음 | 평온 | 회청색 | 천천히 숨쉬기 |
| `THINKING` | 분석 요청 중 | 갸웃, 눈 굴림 | 회청색 | 점 3개 |
| `SAFE` | 0~30 | 웃음 | 초록 | 살짝 통통 |
| `CAUTION` | 31~60 | 걱정 | 주황 | 좌우로 작게 흔들림 |
| `DANGER` | 61~100 | 놀람·슬픔 | 빨강 | 흔들림 + 짧은 진동 1회 |
| `PRAISE` | 대안으로 바꿨거나 위험→안전으로 고쳐 씀 | 하트 눈 | 분홍 | 반짝이, 2초 후 이전 상태로 |
| `OFFLINE` | 네트워크·서버 오류, 할당량 초과 | 졸림 | 회색 | 없음 |

상태 전이는 `MoodStateMachine` 하나가 담당한다. 입력: 분석 결과, 요청 시작/실패, 텍스트 교체 이벤트. 출력: `StateFlow<MoodState>`.

```
IDLE ──입력──▶ THINKING ──결과──▶ SAFE / CAUTION / DANGER
  ▲                 │                     │
  │              오류│                     │대안으로 바꾸기
  │                 ▼                     ▼
  └──입력창 비움── OFFLINE              PRAISE ──2초──▶ (새 결과 상태)
```

---

## 5. 안드로이드 아키텍처

### 5.1 기술 스택

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| UI | Jetpack Compose + Material 3 | 선언형 UI, 오버레이까지 같은 코드로 |
| 구조 | MVVM + 단방향 데이터 흐름 (UiState / Event) | 테스트 쉬움, 면접 설명 쉬움 |
| DI | Hilt | 서비스·뷰모델·워커 공통 주입 |
| 비동기 | Coroutines + Flow | 디바운스, 취소, 상태 구독 |
| 로컬 DB | Room (정식 마이그레이션) | 기록·통계 |
| 설정 | DataStore (Preferences) | 토글, 기준 점수, 캐릭터 설정 |
| 네트워크 | Retrofit + OkHttp + kotlinx.serialization | 프록시 서버 호출 |
| 인증 | Firebase Auth(익명) + App Check(Play Integrity) | 키 없이 앱·사용자 확인 |
| 이미지 | Coil + Photo Picker | 캐릭터 사진, 권한 불필요 |
| 차트 | Compose Canvas 직접 그림 | 라이브러리 의존 없음 |
| 품질 | JUnit, kotlinx-coroutines-test, Turbine (v2.0). Compose UI Test, Crashlytics, GitHub Actions는 다음 단계 | |

### 5.2 모듈 구조

```
EmotionGuard/
├── app/                      Application, MainActivity, 내비게이션, Hilt 진입점
├── core/
│   ├── model/                순수 Kotlin 모델 (AnalysisResult, Category, MoodState, ChatLine …)
│   ├── common/               디스패처, Result 타입, 시간 유틸
│   ├── database/             Room DB, DAO, 마이그레이션
│   ├── datastore/            사용자 설정, 캐릭터 스킨 설정
│   ├── network/              프록시 API 클라이언트, 인증 토큰 인터셉터
│   ├── analysis/             PreFilter, PiiMasker, AnalysisRepository
│   └── designsystem/         테마, 색상 토큰, 공통 컴포넌트, 캐릭터 렌더러
└── feature/
    ├── guard/                접근성 서비스, 감지 계층, 오버레이(Compose), 타일, 알림
    ├── home/                 홈 (토글, 요약)
    ├── history/              기록, 차트
    ├── character/            캐릭터 꾸미기
    ├── settings/             설정, 진단 화면
    └── onboarding/           온보딩, 동의, 권한 안내
```

의존 방향: `app → feature → core`. feature끼리는 서로 의존하지 않는다.

### 5.3 감지 계층

v2의 핵심 설계는 **"텍스트를 어디서 얻는지"를 인터페이스 뒤로 숨기는 것**이다. 지금은 접근성 서비스, 출시 버전에서는 IME(키보드)로 바꿔 끼울 수 있다.

```kotlin
/** 사용자가 입력 중인 텍스트와 주변 맥락을 제공하는 출처. */
interface InputSource {
    val events: Flow<InputEvent>
    fun replaceText(newText: String): Boolean
}

sealed interface InputEvent {
    data class Focused(val app: MessengerApp) : InputEvent
    data class TextChanged(val text: String, val context: ChatContext) : InputEvent
    data class Sent(val text: String, val confidence: SendConfidence) : InputEvent
    data object Blurred : InputEvent
}
```

구현체:
- `AccessibilityInputSource` (v2): `EmotionGuardService`의 이벤트를 `InputEvent`로 변환
- `ImeInputSource` (향후): 커스텀 키보드에서 직접 텍스트 수신, 맥락은 없음

**메신저별 프로필**

```kotlin
data class MessengerProfile(
    val packageName: String,
    val displayName: String,
    val inputFieldIds: List<String>,      // 예: "com.kakao.talk:id/message_edit_text"
    val sendButtonIds: List<String>,
    val sendButtonLabels: List<String>,   // contentDescription 대비책: "전송", "Send"
    val bubbleTextIds: List<String>,
    val noisePatterns: List<Regex>,       // 시간, 날짜 구분선, 안 읽음 숫자
)
```

- 프로필에 뷰 ID가 있으면 ID로 찾고, 없거나 실패하면 v1의 위치 기반 추정으로 넘어간다.
- 서비스 설정에 `flagReportViewIds`를 켜서 ID를 받는다.
- 맥락에는 신뢰도(`0.0~1.0`)를 붙인다. 0.5 미만이면 맥락 없이 분석한다. 잘못 읽은 맥락은 없는 것보다 해롭다.
- 기본 지원: 카카오톡, Google Messages, 인스타그램 DM, 텔레그램. 뷰 ID는 실기기에서 진단 화면으로 확인한 뒤 채운다.

**진단 화면** (설정 → 진단): 현재 앱 패키지, 입력창 감지 여부, 읽은 말풍선 수와 내용, 전송 버튼 발견 여부, 마지막 분석 지연 시간. 메신저가 업데이트돼 감지가 깨졌을 때 원인을 바로 알 수 있다.

### 5.4 전송 판단

세 가지 신호를 1.5초 창 안에서 조합한다.

| 신호 | 감지 방법 |
| --- | --- |
| A. 전송 버튼 클릭 | `TYPE_VIEW_CLICKED` + 프로필의 전송 버튼 ID 또는 라벨 |
| B. 입력창 비워짐 | 두 글자 이상 → 0글자로 한 번에 바뀜 (v1 방식) |
| C. 새 내 말풍선 | 화면에 직전 입력과 같은 텍스트의 내 말풍선이 새로 나타남 |

| 조합 | `SendConfidence` | 통계 반영 |
| --- | --- | --- |
| A 또는 C 포함 | `CONFIRMED` | 예 |
| B만 | `LIKELY` | 예 (설정에서 제외 가능) |
| 그 외 | 기록 안 함 | |

### 5.5 분석 파이프라인

```
TextChanged
  │  debounce 1.2s, 최소 2글자, 같은 문장이면 건너뜀
  ▼
PreFilter (기기 안)
  │  ├─ 확실히 무난 → SAFE로 바로 표시, 서버 호출 안 함
  │  └─ 애매/위험 → 계속
  ▼
PiiMasker (기기 안) : 전화번호·이메일·주민번호·카드·계좌 → [전화번호] 등
  ▼
AnalysisRepository
  │  ├─ 메모리 캐시 (같은 문장 + 같은 맥락 → 재사용, 최대 50개)
  │  └─ 새 입력이 오면 진행 중 요청 취소 (collectLatest)
  ▼
ProxyApi.analyze()  →  서버  →  Claude
  ▼
AnalysisResult → MoodStateMachine → 오버레이
```

**PreFilter 규칙** (v2.0은 규칙 기반, 향후 온디바이스 모델로 교체 가능)
- 5글자 이하 + 위험 사전 단어 없음 → 무난
- 인사·감사·확인류 패턴("ㅇㅋ", "고마워", "좋아", "ㅋㅋ"만 있음) → 무난
- 욕설·비하 사전 단어 포함 → 반드시 서버 분석 (변형 표기 정규화 후 비교: "ㅅㅂ", "시1발" 등)
- 그 외 → 서버 분석

목표: 실제 대화의 약 40~60% 문장을 서버 호출 없이 처리.

### 5.6 Compose 오버레이

접근성 서비스는 Activity가 아니라서 `ComposeView`를 바로 붙이면 `ViewTreeLifecycleOwner`가 없다는 오류가 난다. 서비스 전용 소유자를 만들어 붙인다.

```kotlin
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    // LifecycleRegistry, SavedStateRegistryController, ViewModelStore를 들고
    // show() 때 RESUMED, hide() 때 DESTROYED로 옮긴다.
}

val view = ComposeView(service).apply {
    setViewTreeLifecycleOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setContent { EmotionGuardTheme { FloatingCharacter(state, onDrag, onTap) } }
}
windowManager.addView(view, params /* TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_FOCUSABLE */)
```

캐릭터 버블과 경고 카드는 별도 윈도우 두 개로 둔다. 버블은 작게 유지해서 메신저 터치를 가리지 않기 위해서다.

---

## 6. 캐릭터 시스템 (교체 가능한 스킨)

### 6.1 요구사항
- 기본 캐릭터는 앱에 내장되어 바로 동작한다.
- 사용자는 **상태별로** 사진을 바꿀 수 있다. 일부만 바꿔도 된다.
- **사진 한 장만** 넣어도 모든 상태에서 쓸 수 있다.
- 언제든 상태별 또는 전체를 기본값으로 되돌릴 수 있다.
- 사진을 바꿔도 상태 구분이 되도록 테두리 색과 동작은 유지한다.

### 6.2 데이터 모델

```kotlin
enum class MoodState { IDLE, THINKING, SAFE, CAUTION, DANGER, PRAISE, OFFLINE }

data class CharacterSkin(
    val mode: SkinMode,                       // DEFAULT, SINGLE_IMAGE, PER_STATE
    val singleImage: String?,                 // 내부 저장소 파일 이름
    val stateImages: Map<MoodState, String>,  // 상태별 파일 이름 (없으면 대비책 사용)
    val showColorRing: Boolean = true,
    val animate: Boolean = true,
)
```

DataStore에 JSON으로 저장한다. 이미지 파일은 `filesDir/skins/`에 복사해 두고(원본 URI 권한은 앱 재시작 후 사라질 수 있으므로) 파일 이름만 기록한다.

### 6.3 렌더링 우선순위

```
상태 S를 그릴 때:
  1. PER_STATE 이고 stateImages[S] 있음 → 그 사진
  2. singleImage 있음                   → 그 사진
  3. 그 외                              → 기본 캐릭터 (Compose Canvas로 그린 표정 S)
그 위에: 상태 색 테두리 링 (showColorRing) + 상태별 동작 (animate)
```

```kotlin
interface CharacterRenderer {
    @Composable fun Render(state: MoodState, modifier: Modifier)
}
class DefaultCharacterRenderer : CharacterRenderer      // Canvas: 얼굴 원 + 눈 + 입 곡선
class ImageCharacterRenderer(skin: CharacterSkin) : CharacterRenderer
```

기본 캐릭터는 이미지 파일이 아니라 **코드로 그린 벡터**다. 표정 사이를 `animateFloatAsState`로 부드럽게 바꿀 수 있고(입 곡률, 눈 크기), 용량이 0이다.

### 6.4 캐릭터 꾸미기 화면
- 상단: 미리보기. 상태 칩(대기·생각·안전·주의·위험·칭찬·오프라인)을 누르면 그 표정으로 바뀜
- 모드 선택: 기본 / 사진 한 장 / 상태별 사진
- 사진 선택: Android Photo Picker (`PickVisualMedia`) → 원형 자르기 화면 → 256×256 WebP로 저장
- [이 상태 기본값으로] [전체 초기화]
- 옵션: 색 테두리 표시, 움직임 효과

### 6.5 향후 확장
- 스킨 팩 공유: `skin.json` + 이미지 묶음을 zip으로 내보내기/가져오기
- Lottie 애니메이션 스킨 (`.json`) 지원
- 프리미엄 전용 기본 캐릭터 추가

---

## 7. 서버 (API 프록시)

### 7.1 왜 서버가 필요한가
앱에 API 키가 들어 있으면 APK를 풀어 키를 꺼낼 수 있다. 상업 서비스의 최소 조건은 **키를 서버에만 두는 것**이다. 서버를 두면 사용자별 호출 제한과 요금제 구분도 같이 해결된다.

### 7.2 구조

```
[Android 앱]
   │  Authorization: Bearer <Firebase ID 토큰>     ← 누구인지 (익명 계정 uid)
   │  X-Firebase-AppCheck: <App Check 토큰>        ← 진짜 우리 앱인지
   ▼
[Cloudflare Worker]  (server/)
   ├─ 토큰 검증 (Google 공개키, jose)
   ├─ 입력 검증 (zod) + 개인정보 가리기 (2차)
   ├─ 할당량 확인 (Durable Object, 사용자 uid별)
   ├─ Claude API 호출 (공식 @anthropic-ai/sdk, 구조화된 출력)
   └─ 결과 + 남은 횟수 반환
   ▼
[Claude API]
```

### 7.3 API

`POST /v1/analyze`

```json
// 요청
{
  "text": "너 진짜 왜 그래?",
  "context": [
    { "speaker": "other", "text": "오늘 회사에서 너무 힘들었어" },
    { "speaker": "me", "text": "무슨 일 있었어?" }
  ],
  "contextConfidence": 0.9
}

// 200 응답
{
  "score": 48,
  "level": "CAUTION",
  "category": "SARCASM",
  "reason": "상대가 힘든 이야기를 하는 중이라 다그치는 말투로 들릴 수 있어요.",
  "alternative": "무슨 일 있었는지 더 얘기해줄래?",
  "quota": { "limit": 100, "remaining": 87, "resetAt": "2026-11-18T00:00:00+09:00" }
}
```

| 상태 코드 | 의미 | 앱 동작 |
| --- | --- | --- |
| 400 | 입력 형식 오류 | 로그만 남김 |
| 401 | 토큰 없음/만료/위조 | 토큰 새로 받아 1회 재시도 |
| 429 | 하루 할당량 또는 분당 제한 초과 | `OFFLINE` 표정 + "오늘 무료 분석을 다 썼어요" |
| 502 | Claude 호출 실패 | `OFFLINE` 표정, 다음 입력 때 재시도 |

`GET /v1/health` : 서버 상태 확인

### 7.4 카테고리

| 코드 | 한국어 |
| --- | --- |
| `NONE` | 해당 없음 |
| `PROFANITY` | 욕설 |
| `BELITTLING` | 비하·무시 |
| `SARCASM` | 비꼼·짜증 |
| `BLAME` | 비난·탓하기 |
| `SEXUAL` | 성적 표현 |
| `HATE` | 혐오·차별 |
| `THREAT` | 위협 |

### 7.5 인증 모드

| 모드 (`AUTH_MODE`) | 용도 | 검증 |
| --- | --- | --- |
| `firebase` | 운영 | Firebase ID 토큰(필수) + App Check 토큰(`APP_CHECK_REQUIRED=true`면 필수) |
| `dev` | 로컬 개발·시연 | `X-Dev-Key` 헤더가 서버 비밀값과 일치, 사용자 ID는 `X-Install-Id` |

### 7.6 할당량과 요금제
- 사용자 uid마다 Durable Object 하나가 오늘 사용량과 최근 1분 사용량을 들고 있다.
- 무료: 하루 `FREE_DAILY_LIMIT`회(기본 100), 분당 `PER_MINUTE_LIMIT`회(기본 20)
- 프리미엄: ID 토큰의 커스텀 클레임 `plan: "premium"`이 있으면 하루 `PREMIUM_DAILY_LIMIT`회(기본 2000)
  - 결제 후 Firebase Admin으로 클레임을 붙이는 구조 (결제 연동은 v2 범위 밖)
- 날짜 기준: 한국 시간 자정

### 7.7 비용

| 항목 | 무료 범위 | 비고 |
| --- | --- | --- |
| Cloudflare Workers | 하루 10만 요청 | 신용카드 불필요 |
| Durable Objects (SQLite) | 무료 플랜 포함 | |
| Firebase Auth (익명) | 무료 | |
| Firebase App Check (Play Integrity) | 하루 1만 건 표준 무료 | |
| **Claude API** | **유료 (호출량만큼)** | 서버 비용과 별개. 유일한 실비용 |

Claude 비용을 줄이는 장치: 기기 안 1차 필터, 같은 문장 캐시, 요청 취소, 짧은 출력(`max_tokens` 제한), effort `low`, 모델을 환경변수로 교체 가능.

---

## 8. 데이터

### 8.1 Room 스키마 (버전 1)

```kotlin
@Entity(tableName = "emotion_log")
data class EmotionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val score: Int,
    val category: Category,
    val warned: Boolean,             // 경고 기준 이상이었는지
    val replaced: Boolean,           // 대안으로 바꿔 보냈는지
    val sendConfidence: SendConfidence,  // 전송 판단 신뢰도
    val analyzedLocally: Boolean,    // PreFilter로 처리했는지
    val originalText: String?,       // 사용자가 켰을 때만
)
```

### 8.2 마이그레이션 정책
v2는 새로 만든 앱이라 DB 버전 1에서 시작한다. v1 앱의 기록은 옮기지 않는다.
- `fallbackToDestructiveMigration()`을 쓰지 않는다
- `exportSchema = true`, 스키마 JSON을 `core/database/schemas/`에 커밋
- 다음 구조 변경부터: 단순 컬럼 추가는 `@AutoMigration(from = 1, to = 2)`, 이름 변경·분리는 `Migration` 직접 작성
- 마이그레이션을 추가할 때 `MigrationTestHelper`로 데이터 보존 테스트를 함께 추가

---

## 9. 개인정보·보안

| 원칙 | 구현 |
| --- | --- |
| 최소 수집 | PreFilter로 무난한 문장은 외부로 안 보냄 |
| 가리기 | 앱(`PiiMasker`)과 서버에서 두 번: 전화번호, 이메일, 주민등록번호, 카드번호, 계좌번호 형식 |
| 저장 안 함 | 서버는 요청 본문을 저장·로그하지 않음 (오류 시 길이·코드만 기록) |
| 원문 저장 기본 꺼짐 | 기기 기록에도 점수와 유형만, 원문은 설정에서 켤 때만 |
| 사용자 통제 | 앱별 켜기/끄기, 오버레이 "잠시 끄기", 기록 전체 삭제 |
| 고지 | 온보딩에서 명시적 고지 + 동의, 설정에 개인정보처리방침 |
| 키 보호 | API 키는 Worker 비밀값(`wrangler secret`)에만 존재 |
| 비밀번호 칸 제외 | `isPassword` 입력창은 절대 읽지 않음 |

---

## 10. 품질

| 영역 | 내용 |
| --- | --- |
| 단위 테스트 | PreFilter, PiiMasker, MoodStateMachine, SendDetector, 통계 집계 |
| Flow 테스트 | Turbine으로 디바운스·취소·상태 전이 |
| DB 테스트 | 통계 계산 테스트 (마이그레이션 테스트는 첫 구조 변경 때) |
| UI 테스트 | 홈 토글, 캐릭터 꾸미기 화면 (Compose UI Test) — 다음 단계 |
| 서버 테스트 | 입력 검증, 가리기, 인증 거부, 할당량 (vitest) |
| CI | GitHub Actions: 앱 `./gradlew lint test`, 서버 `npm test && npm run typecheck` — 다음 단계 |
| 모니터링 | Workers 로그·분석(서버). Crashlytics(앱)는 다음 단계 |

---

## 11. Play 출시 전략

접근성 서비스는 Play 정책상 용도 심사가 엄격하다. v2는 다음을 준비한다.
1. 온보딩의 **명시적 고지** 화면 (수집 데이터, 목적, 전송 대상)
2. 접근성 서비스 설명문을 실제 기능과 정확히 일치시킴
3. `InputSource` 인터페이스 덕분에 반려 시 **IME(커스텀 키보드) 방식**으로 전환 가능
   - IME 방식: 심사 수월, 텍스트를 정식으로 받음 / 대화 맥락 없음, 캐릭터는 키보드 상단 영역에 표시

포트폴리오 단계에서는 APK + 시연 영상으로 배포한다.

---

## 12. 구현 단계

| 단계 | 내용 | 결과물 |
| --- | --- | --- |
| 1 | 프록시 서버 | `server/` Worker, 테스트, 배포 가이드 |
| 2 | 앱 골격 | 멀티 모듈, Hilt, Compose 테마, 내비게이션 |
| 3 | 데이터 계층 | Room v3 + 마이그레이션, DataStore, 네트워크 클라이언트 |
| 4 | 감지 계층 | `InputSource`, 메신저 프로필, 전송 판단, 맥락 읽기 |
| 5 | 분석 파이프라인 | PreFilter, PiiMasker, 캐시, 취소 |
| 6 | 오버레이 + 캐릭터 | Compose 오버레이, 상태 머신, 기본 렌더러 |
| 7 | 화면 | 온보딩, 홈(토글), 기록, 캐릭터 꾸미기, 설정·진단, 타일, 알림 |
| 8 | 품질 | 테스트, CI, Crashlytics |

---

## 13. 열린 질문
- 카카오톡 최신 버전의 실제 뷰 ID (실기기 진단 화면으로 확인 필요)
- 1차 필터의 무난 판정 기준을 얼마나 공격적으로 잡을지 (비용 vs 놓침)
- 프리미엄 결제 수단 (Google Play Billing) 연동 시점

---

## 14. 구현 현황 (2026-11-17)

| 단계 | 상태 |
| --- | --- |
| 1. 프록시 서버 | 완료. 단위 테스트 49개, 로컬 `wrangler dev`로 인증·할당량·429 확인. 실제 Claude 호출은 미확인 (API 키 필요) |
| 2~7. 앱 | 완료. 14개 모듈, 디버그 APK 빌드, 단위 테스트 38개 통과, 에뮬레이터(API 34)에서 온보딩·직접 테스트 → 로컬 서버(테스트 모드) 흐름 확인 |
| 메신저 위 오버레이 | 에뮬레이터 Google 메시지에서 확인: 입력 → 위험 표정 → 카드 → 대안으로 바꾸기 → 칭찬 → 안전 → 전송(확정) → 기록·"바꿔 보냄" 반영 |
| 실기기 메신저 감지 | 미확인. 카카오톡 등 실제 메신저의 뷰 ID와 전송 판단은 실기기 진단 화면으로 확인 필요 |

에뮬레이터 확인 중 고친 점
- Google 메시지는 앱이 입력창을 코드로 바꾸거나 비울 때 "글 변경" 이벤트를 보내지 않는다 → 화면 변경 이벤트 때 입력창을 직접 다시 읽고, 대안으로 바꾼 문장은 바로 분석 대기열에 넣는다.
- 대안으로 바꾼 뒤 칭찬이 끝나면 옛날 "위험" 표정으로 돌아가던 문제 → 바꾸면 이전 결과를 버린다.
- 화면을 거의 다 채운 긴 말풍선을 나/상대로 구분하지 못하던 문제 → 양쪽 여백을 비교한다.
- 대안으로 바꿔 보낸 메시지가 "경고"로 세지지 않아 "바꿔 보낸 비율"이 0이던 문제 → 바꾼 메시지는 경고로 기록한다.
- 우리 앱 화면으로 돌아와도 캐릭터가 남던 문제 → 우리 앱의 Activity가 열리면 숨긴다.
- 전송 버튼 라벨이 "SMS 보내기"처럼 앞에 다른 말이 붙는 경우와, 눌린 뷰가 버튼 안쪽 아이콘인 경우를 처리한다. (Google 메시지는 Compose라 클릭 이벤트 자체가 오지 않을 수 있어, 이때는 새 말풍선 확인으로 전송을 확정한다.)
- 시스템 문구 "○○님과 문자 메시지로 대화 중"을 대화로 읽던 문제 → 노이즈 패턴에 추가.
| 8. 품질 | 일부. Compose UI Test, Crashlytics, GitHub Actions는 다음 단계 |

빌드 도구 선택: AGP 8.13 · Kotlin 2.2.20 · Gradle 8.14 · Compose BOM 2025.10. 최신(AGP 9.x)보다 한 세대 앞이지만 검증된 조합이라 골랐다. Android Studio가 업그레이드를 제안하면 AGP Upgrade Assistant로 올리면 된다.

---

## 15. v2.1 추가 기능 (2026-11-17 결정)

구현 순서는 의존 관계대로 정했다: 서버 응답 형식 → 관계 → 성장 → 리포트 → 접근성 점검.

### 15.1 대안 문장 여러 개 + 말투 선택
- 서버 응답에 `alternatives: [{ tone, text }]`를 추가한다. 위험·주의일 때 최대 3개, 안전이면 빈 배열.
- 말투(`tone`): `SOFT` 부드럽게 · `FIRM` 단호하지만 정중하게 · `PLAYFUL` 가볍게. 할 말(거절, 불만)은 지우지 않고 말투만 바꾼다.
- 기존 `alternative` 필드는 첫 번째 대안으로 계속 보낸다 (구버전 앱 호환).
- 경고 카드에서 말투 칩을 골라 미리 본 뒤 [이걸로 바꾸기]를 누른다.

### 15.2 관계별 모드
- 요청에 `relationship`: `FRIEND` 친구 · `WORK` 직장 · `PARTNER` 연인 · `FAMILY` 가족 · `UNKNOWN` 기본.
- 판단 규칙: 직장은 반말·명령조·비꼼을 더 엄격하게, 대안은 존댓말. 친구는 서로 장난치는 맥락을 더 허용. 연인·가족은 서운함·무시하는 말투에 민감하게.
- 대화방 구분: 채팅방 화면 상단의 제목(상대 이름)을 읽어 `SHA-256(패키지 + 제목)`을 키로 쓴다. 이름 자체는 저장하지 않는다.
- 대화방별 관계는 경고 카드의 관계 칩으로 정한다. 정하지 않은 방은 설정의 기본 관계를 쓴다.
- 기기 안 1차 필터는 직장 모드에서 더 엄격하다 (짧은 문장도 서버로).

### 15.3 캐릭터 성장
- 경험치(XP)와 레벨(1~10). 규칙은 순수 함수 `GrowthRules`로 두고 단위 테스트한다.

| 행동 | XP |
| --- | --- |
| 안전한 메시지 전송 | +2 |
| 대안으로 바꿔 보냄 | +15 |
| 위험 메시지를 그대로 보냄 | 0, 연속 기록 끊김 |
| 위험 메시지 없이 하루를 넘김 (연속 N일) | 그날 첫 전송 때 +5×N (최대 +35) |
| 하루 최대 | 60 (반복 전송으로 올리는 것 방지) |

- 레벨에 따라 장식이 열린다: Lv2 꽃 · Lv3 동그란 안경 · Lv5 헤드폰 · Lv7 파티 모자 · Lv10 왕관. 사진 스킨에도 얹을 수 있다.
- 레벨업하면 캐릭터가 칭찬 표정을 짓고 알림으로 알려준다.

### 15.4 주간 리포트 알림
- WorkManager 주기 작업, 매주 일요일 20시 전후. 설정에서 켜고 끈다 (기본 켜짐).
- 내용: 보낸 메시지 수, 평균 감정 온도와 지난주 대비, 바꿔 보낸 횟수, 가장 많았던 유형, 현재 레벨. 문장 생성은 순수 함수 `WeeklyReport.build`로 테스트한다.
- 설정의 [지금 리포트 받아보기]로 바로 확인할 수 있다 (시연용).
- 메시지 원문은 알림에 넣지 않는다.

### 15.5 TalkBack(화면 낭독) 점검
- 모든 아이콘·캐릭터에 의미 있는 설명, 장식용은 설명 없음.
- 떠 있는 캐릭터: "감정 가드 캐릭터, 위험 85점" + 두 번 탭 동작 이름("경고 카드 열기"). 상태가 바뀌면 바로 읽어주도록 live region.
- 토글·스위치 행은 행 전체가 하나의 스위치로 읽히게 (`toggleable`), 카드·기록 행은 한 덩어리로 읽히게 (`mergeDescendants`).
- 차트는 막대 대신 "최근 7일 하루 평균: 월 20도, 화 기록 없음…" 요약을 읽어준다.
- 섹션 제목은 heading으로 표시해 제목 단위로 이동할 수 있게.

### 15.6 구현 현황 (2026-11-17)

다섯 기능 모두 구현했고 에뮬레이터(Google 메시지, 서버 테스트 모드)에서 확인했다.

| 기능 | 확인한 것 |
| --- | --- |
| 말투별 대안 | 경고 카드에 부드럽게·단호하지만 정중하게·가볍게 칩, 고른 말투로 바꾸기 |
| 관계별 모드 | 방 제목(상대 이름) 인식, 관계 칩 선택 → 즉시 재분석, 직장이면 점수 상승·가벼운 말투 제외 |
| 캐릭터 성장 | 바꿔 보내기 2번 → Lv 2 레벨업 알림, 꽃 장식 열림·적용 |
| 주간 리포트 | [지금 리포트 받아보기] → 요약 알림 도착 |
| TalkBack | 접근성 트리 덤프로 토글·캐릭터·성장 카드 설명 확인 |

확인하지 못한 것
- 실제 TalkBack을 켠 상태에서의 낭독 순서와 떠 있는 캐릭터의 live region 알림 (에뮬레이터에 TalkBack 앱 없음)
- 꽃 외의 장식(안경·헤드폰·파티 모자·왕관) 그림 — 레벨이 낮아 화면으로 보지 못함
- 주간 리포트 주기 작업이 실제 일요일 20시에 실행되는지 (예약 계산은 단위 테스트로 확인)
- 실제 Claude의 관계·말투 판단 품질 (서버 테스트 모드로만 확인)