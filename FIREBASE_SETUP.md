# Firebase 설정 가이드

## 1단계: Firebase 프로젝트 생성
1. https://console.firebase.google.com 접속
2. **프로젝트 추가** → 이름 입력 → 계속

## 2단계: Android 앱 등록
1. 프로젝트 홈 → **Android 아이콘** 클릭
2. 패키지명: `com.goalwidget`
3. **앱 등록** → `google-services.json` 다운로드
4. 다운로드한 파일을 `app/google-services.json`에 **덮어쓰기**

## 3단계: Realtime Database 생성 ⚠️ 가장 중요
1. 왼쪽 메뉴 → **Realtime Database** → **데이터베이스 만들기**
2. 위치 선택:
   - **asia-northeast1 (도쿄)** 권장 (한국에서 빠름)
   - 단, 이 경우 DB URL이 다름 → 아래 4단계 필수
3. 보안 규칙: **테스트 모드** 선택 → 확인

## 4단계: google-services.json에 DB URL 추가 ⚠️ 필수
Firebase 콘솔 → Realtime Database 화면 상단에 URL이 표시됨:
- us-central1: `https://프로젝트ID-default-rtdb.firebaseio.com`
- asia-northeast1: `https://프로젝트ID-default-rtdb.asia-southeast1.firebasedatabase.app`

`app/google-services.json` 파일을 열어서 `"services"` 섹션에 추가:
```json
{
  "services": {
    "appinvite_service": { ... },
    "firebase_url": "https://여기에-실제-URL-입력"
  }
}
```
또는 더 간단하게, `client` 배열 바깥 `project_info`에 추가:
```json
{
  "project_info": {
    "project_number": "...",
    "project_id": "...",
    "storage_bucket": "...",
    "firebase_url": "https://프로젝트ID-default-rtdb.asia-southeast1.firebasedatabase.app"
  },
  ...
}
```

## 5단계: 보안 규칙 설정
Realtime Database → **규칙** 탭:
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

## 6단계: 익명 로그인 활성화
1. **Authentication** → **시작하기**
2. **로그인 방법** → **익명** → **사용 설정** → **저장**

## 문제 진단
`DB 조회 타임아웃` 에러가 뜨면:
- Logcat에서 `DB URL:` 로그를 확인
- Firebase 콘솔의 실제 URL과 비교
- 다르면 google-services.json에 `firebase_url` 추가
