# Firebase 설정 가이드

## 1단계: Firebase 프로젝트 생성

1. https://console.firebase.google.com 접속
2. **프로젝트 추가** 클릭
3. 프로젝트 이름 입력 (예: `goal-widget-family`)
4. Google Analytics는 선택 사항 → 계속

## 2단계: Android 앱 등록

1. 프로젝트 홈에서 **Android 아이콘** 클릭
2. 패키지명 입력: `com.goalwidget`
3. 앱 닉네임 입력 (예: 목표위젯)
4. **앱 등록** 클릭
5. `google-services.json` 다운로드
6. 다운로드한 파일을 이 프로젝트의 `app/` 폴더에 덮어쓰기

## 3단계: Realtime Database 활성화

1. Firebase 콘솔 왼쪽 메뉴 → **Realtime Database**
2. **데이터베이스 만들기** 클릭
3. 위치: `asia-northeast1 (Tokyo)` 선택 (한국에서 가장 가까움)
4. 보안 규칙: **테스트 모드에서 시작** 선택 후 확인

## 4단계: 보안 규칙 설정

Realtime Database → **규칙** 탭에서 아래 내용으로 교체:

```json
{
  "rules": {
    "groups": {
      "$groupCode": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

> 이 규칙은 로그인한 사용자(익명 포함)만 읽기/쓰기 가능하도록 제한합니다.

## 5단계: 익명 로그인 활성화

1. Firebase 콘솔 → **Authentication**
2. **시작하기** 클릭
3. **로그인 방법** 탭 → **익명** 클릭
4. **사용 설정** 켜기 → **저장**

## 6단계: 빌드 및 실행

1. Android Studio에서 프로젝트 열기
2. Gradle Sync 실행
3. 빌드 후 실행

## 사용법

### 그룹 만들기 (첫 번째 기기)
1. 앱 실행 → 하단 👨‍👩‍👧 버튼 탭
2. 닉네임 입력 후 **그룹 만들기**
3. 생성된 6자리 코드를 가족에게 카톡으로 전송

### 그룹 참여 (두 번째 기기)
1. 앱 실행 → 하단 👨‍👩‍👧 버튼 탭
2. 닉네임 입력
3. 받은 코드 입력 → **참여하기**
4. 자동으로 목표 목록 실시간 동기화 시작

## 무료 플랜 한도 (Spark Plan)
| 항목 | 한도 |
|---|---|
| 동시 접속 | 100개 |
| 저장 용량 | 1 GB |
| 다운로드 | 10 GB/월 |
| 익명 계정 | 무제한 |

가족 몇 명이 쓰기에는 충분합니다.
