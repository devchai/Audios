# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

**오디오스(Audios)** - 안드로이드 미디어 변환 및 편집 앱
- 비디오→오디오 변환 (MP4, AVI, MOV → MP3, WAV, AAC)
- 오디오→오디오 변환 (포맷 변경, 비트레이트 조정)
- 오디오/비디오 편집 기능

## 개발 환경

### 기술 스택
- **Language**: Java 8
- **Min SDK**: API 29 (Android 10)
- **Target SDK**: API 34 (Android 14)
- **Build System**: Gradle
- **Architecture**: MVVM + Repository Pattern
- **View Binding**: Enabled
- **IDE**: Android Studio Iguana

### 주요 라이브러리
- Android Native API (MediaExtractor, MediaMuxer): 미디어 변환 처리 (Phase 4: FFmpeg 완전 대체)
- Material Design 3: UI 컴포넌트
- Timber: 로깅
- Calligraphy3: 커스텀 폰트
- AdMob & Adlib: 광고 통합

## 빌드 및 실행 명령어

```bash
# 디버그 빌드
./gradlew assembleDebug

# 릴리즈 빌드
./gradlew assembleRelease

# 설치
./gradlew installDebug

# 테스트 실행
./gradlew test

# 연결된 기기에서 테스트
./gradlew connectedAndroidTest

# 클린 빌드
./gradlew clean build

# Lint 검사
./gradlew lint
```

## 프로젝트 구조

```
app/src/main/java/com/devc/lab/audios/
├── activity/           # Activity 클래스
│   ├── MainActivity    # 메인 화면
│   └── SplashActivity  # 스플래시 화면
├── base/              # 기본 클래스
│   ├── BaseActivity   # Activity 기본 클래스
│   └── AudiosApplication # Application 클래스
├── manager/           # 매니저 클래스 (싱글톤 패턴)
│   ├── AudioConversionManager # 통합 오디오 변환 관리 (Phase 4: Native API 기반)
│   ├── NativeMediaInfoManager # 미디어 정보 추출 (Native API)
│   ├── NativeAudioExtractorManager # 오디오 추출 (Native API)
│   ├── NativeAudioTrimManager # 오디오 자르기 (Native API)
│   ├── AudioTrimManager # 오디오 편집 통합 관리
│   ├── FileManager    # 파일 시스템 관리
│   ├── PermissionManager # 권한 관리
│   ├── DialogManager  # 다이얼로그 관리
│   ├── ToastManager   # 토스트 메시지
│   └── LoggerManager  # 로깅 관리
├── model/            # 데이터 모델 및 ViewModel
│   └── MainViewModel # 메인 화면 ViewModel
├── ads/              # 광고 관련
└── utils/            # 유틸리티 클래스
```

## 아키텍처 패턴

### MVVM + Repository Pattern
- **View**: Activity/Fragment - UI 표시 및 사용자 입력 처리
- **ViewModel**: 비즈니스 로직 및 데이터 관리
- **Repository**: 데이터 소스 추상화
- **Manager**: 싱글톤 패턴으로 시스템 기능 관리

### 데이터 흐름
```
View → ViewModel → Repository → Manager/DataSource
     ←            ←            ←
```

## 핵심 기능 구현 가이드

### 1. 미디어 변환 (AudioConversionManager)
- Android Native API(MediaExtractor/MediaMuxer)를 통한 비디오/오디오 변환
- Phase 4 완료: FFmpeg 완전 제거, Native API로 교체
- NativeAudioExtractorManager, NativeAudioTrimManager 통합 관리
- 백그라운드 서비스로 변환 진행
- 진행률 콜백 처리

### 2. 파일 관리 (FileManager)
- Scoped Storage 정책 준수 (API 29+)
- 내부 저장소 구조: `/Audios/Converted`, `/Audios/Edited`
- MediaStore API 활용

### 3. 권한 관리 (PermissionManager)
- READ_EXTERNAL_STORAGE 권한 처리
- 런타임 권한 요청 로직

### 4. UI 디자인
- Material Design 3 컴포넌트 사용
- View Binding으로 뷰 참조
- 다크 모드 지원 구현 예정

## 코딩 컨벤션

### 네이밍 규칙
- **클래스**: PascalCase (예: `MainActivity`)
- **메서드/변수**: camelCase (예: `convertVideo`)
- **상수**: UPPER_SNAKE_CASE (예: `MAX_FILE_SIZE`)
- **리소스 ID**: snake_case (예: `btn_convert`)

### 파일 구조
- 하나의 클래스당 하나의 파일
- Manager 클래스는 싱글톤 패턴 적용
- Activity는 activity 패키지에 배치

### XML 리소스
- **Layout**: `activity_`, `fragment_`, `item_`, `dialog_` 접두사 사용
- **Strings**: `strings.xml`에 모든 문자열 정의
- **Colors**: `colors.xml`에 색상 정의
- **Styles**: Material Design 3 테마 기반

## 개발 로드맵

### Phase 1: Native API 기반 구조 설정 (✅ 완료)
- [x] 프로젝트 구조 설정
- [x] 권한 관리 시스템
- [x] NativeMediaInfoManager 구현 (미디어 메타데이터 추출)

### Phase 2: 오디오 추출 기능 (✅ 완료)  
- [x] NativeAudioExtractorManager 구현
- [x] MediaExtractor + MediaMuxer 기반 오디오 추출
- [x] 비디오→오디오 변환 (M4A, WEBM 지원)
- [x] 진행률 콜백 시스템

### Phase 3: 오디오 편집 기능 (✅ 완료)
- [x] NativeAudioTrimManager 구현  
- [x] 시간 기반 오디오 자르기
- [x] 비율 기반 오디오 자르기
- [x] AudioTrimManager 통합 관리

### Phase 4: FFmpeg 완전 제거 및 통합 (✅ 완료)
- [x] AudioConversionManager 통합 관리자 구현
- [x] FFmpegManager → AudioConversionManager 교체
- [x] FFmpeg Kit 종속성 완전 제거
- [x] Native API 기반으로 완전 전환
- [x] 빌드 검증 및 테스트 완료

### 향후 계획: UI/UX 개선 및 출시
- [ ] 파일 선택기 UI 구현
- [ ] 기본 미디어 플레이어 구현
- [ ] 다크 모드 지원
- [ ] 파형 시각화
- [ ] 배치 변환 기능
- [ ] 출시 준비

## 성능 목표
- 앱 시작 시간: Cold start < 2초
- 변환 속도: 실시간 대비 3배 이상
- 메모리 사용: 최대 200MB
- 크래시율: 0.5% 미만

## 테스트 전략
- Unit Test: JUnit 4
- UI Test: Espresso
- 통합 테스트: 실제 미디어 파일로 변환 테스트

## 주의사항
- Scoped Storage 정책으로 인해 API 29 이상에서 파일 접근 방식 변경
- ✅ Phase 4 완료: FFmpeg 완전 제거로 APK 크기 대폭 감소
- 백그라운드 작업 시 배터리 최적화 고려
- Android Native API 특성상 지원 포맷 제한 (M4A, WEBM 중심)
- MediaExtractor/MediaMuxer 호환성 테스트 필수

## 디버깅 팁
- Timber 로그 확인: `LoggerManager` 통해 상세 로그 출력
- Native API 디버깅: `AudioConversionManager`의 상세 로그 확인
- 오디오 추출 디버깅: `NativeAudioExtractorManager` 로그 모니터링
- 오디오 편집 디버깅: `NativeAudioTrimManager` 로그 확인
- 권한 문제: `PermissionManager` 로그 확인