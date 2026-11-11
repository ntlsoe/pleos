# Pleos Safety Window

**앱 이름:** Pleos Safety Window

**한 줄 소개:** 차량 A필러 사각 영역의 시야 가림을 기하학적으로 분석해 운전자에게 참고 정보를 제공하는 AAOS 프로토타입입니다.

## 실행 방법

1. Android Studio에서 이 폴더를 Gradle 프로젝트로 엽니다.
2. JDK 17과 Android SDK Platform 34를 설정합니다.
3. 명령행에서는 Windows 기준 다음 명령으로 빌드합니다.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Pleos Connect SDK 의존성은 `nexus-playground.pleos.ai` 저장소에서 받아옵니다. 빌드 환경에 해당 SDK에 접근할 권한이 필요할 수 있으며, SDK 바이너리는 이 공개 소스 패키지에 포함하지 않았습니다. Gradle 테스트 워커의 경로 제약 때문에 Windows에서는 ASCII 문자 경로에서 빌드하는 것을 권장합니다.

APK는 Android Automotive 기능과 필요한 시스템 서비스를 제공하는 호환 환경에서 설치·실행하세요. 일반 Android 에뮬레이터에는 네이티브 Car UX 기능이 없을 수 있습니다.

## 테스트 및 환경

기준 소스의 전체 내부 검증 기록은 Windows, JDK 17, Android SDK/compile SDK 34 환경에서 Product JVM 492/492, advisory JVM 10/10, Android instrumented 1/1 통과를 기록합니다. 이 공개본은 내부 감사자료와 그 자료에 종속된 앱 테스트 코드를 제외한 배포용 소스 스냅샷이므로 해당 전체 테스트 결과는 이 폴더에서 재실행한 결과가 아닙니다. `simulation/tests`에는 독립 시뮬레이션 테스트 소스가 포함됩니다.

## Release APK

저장소 내 APK 경로: [`apk/PleosSafetyWindowTeam_PleosSafetyWindow_release.apk`](apk/PleosSafetyWindowTeam_PleosSafetyWindow_release.apk)

- 크기: 40,098,338 bytes
- SHA-256: `0c3da53fe4a5ea508b9fd4a0d55e552ee630ed030dee1cc9fd1fb1467929d741`
- 패키지: `ai.pleos.app.safetywindow`
- 버전: `1.0.0` (versionCode 1)

## 소스 기준 및 제한사항

앱 소스와 빌드 설정은 기준 커밋 `7110eb42057c4404dcbc0034aecb08a65bd18721` (`v48-verified-source-baseline`)에서 추출했습니다. 공개 제출본은 심사에 필요한 코드와 데이터를 담도록 별도의 파일 범위로 만든 스냅샷입니다. 공개 데이터 출처와 라이선스 요약은 [`DATA_AND_LICENSES.md`](DATA_AND_LICENSES.md)에 있습니다.

SET-07 시나리오는 합성 기하·자세 입력을 사용한 동작 검증입니다. 실제 차량 검증, 보행자 감지, 충돌 예측 또는 사고 예방을 의미하지 않습니다. 네이티브 Car UX 동작과 실차 물리/음향 TTS도 검증하지 않았으며, Release 및 REAL_INPUT 경로는 fail-closed로 제한됩니다.
