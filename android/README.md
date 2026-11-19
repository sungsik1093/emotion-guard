# 감정 가드 안드로이드 앱

설계 배경은 [../docs/DESIGN.md](../docs/DESIGN.md)를 참고하세요.

## 모듈

```
app/                      Application, MainActivity, 내비게이션 (화면 연결은 여기서만)
build-logic/convention/   공통 빌드 규칙 플러그인 (android.library, android.feature, android.room …)
core/
  model/                  순수 Kotlin 모델: AnalysisResult, MoodState, CharacterSkin, UserSettings …
  common/                 디스패처, 접근성 활성 확인, 진단 데이터 통로
  database/               Room DB (스키마 export), 기록 저장소, 통계 계산
  datastore/              설정 저장소, 캐릭터 스킨 저장소 (사진 복사·정리)
  network/                프록시 API (Retrofit), 인증 헤더 (Firebase / dev), 오류 변환
  analysis/               1차 필터, 개인정보 가리기, 캐시, 분석 저장소
  designsystem/           테마, 기본 캐릭터(Canvas), 캐릭터 아바타, 토글·온도계
feature/
  guard/                  접근성 서비스, 감지 계층, 전송 판단, 캐릭터 상태 머신, 오버레이, 타일, 알림
  onboarding/             온보딩 3장 (명시적 고지 + 동의, 권한 안내)
  home/                   VPN식 토글, 오늘 요약, 직접 테스트
  history/                주간/월간 차트, 요약, 최근 기록
  character/              캐릭터 꾸미기 (Photo Picker)
  settings/               설정, 진단 화면
```

의존 방향: `app → feature → core`. feature 모듈끼리는 서로 모릅니다.

## 코드 읽는 순서 추천

모든 파일 맨 위에 그 파일이 하는 일을 한국어로 적어뒀습니다.

1. `feature/guard/.../GuardSession.kt` — 전체 흐름 (감지 → 분석 → 캐릭터 → 기록)
2. `feature/guard/.../input/InputSource.kt` — 감지 계층 인터페이스 (IME로 바꿔 끼울 수 있게)
3. `feature/guard/.../input/AccessibilityInputSource.kt`, `ChatContextReader.kt`, `SendDetector.kt`
4. `feature/guard/.../mood/MoodStateMachine.kt` — 캐릭터 표정 전이
5. `feature/guard/.../overlay/` — Compose 오버레이 (서비스용 LifecycleOwner 포함)
6. `core/analysis/` — 1차 필터, 개인정보 가리기, 캐시
7. `core/designsystem/character/` — 기본 캐릭터와 사진 스킨 렌더링

## 설정 (`local.properties`, git에 올라가지 않음)

| 키 | 설명 |
| --- | --- |
| `PROXY_BASE_URL` | 프록시 서버 주소. 에뮬레이터에서 PC의 로컬 서버는 `http://10.0.2.2:8787/` |
| `DEV_KEY` | dev 인증 키 (`server/.dev.vars`의 `DEV_KEY`와 같게) |
| `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER` | 채우면 Firebase 익명 인증 + App Check(Play Integrity)를 씁니다. 비어 있으면 dev 인증 |

`google-services.json` 없이 위 값으로 Firebase를 초기화합니다 (`core/network/.../AuthHeaders.kt`).

## 빌드와 테스트

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest :core:model:test
```

## 바꿀 만한 곳

| 무엇 | 어디 |
| --- | --- |
| 지원 메신저 목록 | `core/model/.../Chat.kt`의 `SupportedMessengers` + `feature/guard/.../input/MessengerProfile.kt` |
| 메신저별 뷰 ID | `MessengerProfiles` (실기기에서 설정 → 진단 화면으로 확인) |
| 디바운스·최소 글자 수 | `GuardSession.DEBOUNCE_MS`, `MIN_LENGTH` |
| 1차 필터 단어 목록 | `core/analysis/.../PreFilter.kt` |
| 캐릭터 표정 | `core/designsystem/.../character/DefaultCharacter.kt`의 `faceOf()` |
| 전송 판단 창 | `SendDetector(windowMillis)`, `AccessibilityInputSource.SEND_WINDOW_MS` |

## 알아둘 점

- 메신저 뷰 ID(`MessengerProfiles`)는 실기기에서 확인되지 않은 초기값입니다. 틀려도 위치 추정으로 동작하지만, 진단 화면으로 확인해서 고치면 맥락 정확도가 올라갑니다.
- Room은 현재 버전 1이고 `fallbackToDestructiveMigration()`을 쓰지 않습니다. 구조를 바꿀 때는 `AppDatabase`의 주석대로 마이그레이션을 추가하세요. 스키마 JSON은 `core/database/schemas/`에 생성됩니다.
- 사진 스킨은 EXIF 회전 정보를 반영하지 않습니다. 세로로 찍은 일부 사진이 돌아가 보일 수 있습니다.
