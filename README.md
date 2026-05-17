# 🎯 목표 달성 위젯 (GoalWidget)

안드로이드 홈 화면 위젯으로 목표 달성률을 한눈에 확인할 수 있는 앱입니다.

## 📱 주요 기능

### 위젯
- **가로 바 형태** 위젯: 목표명 + 프로그레스 바 + 달성률% + 달성/목표 수치
- 위젯 하나에 목표 하나 표시
- **여러 위젯** 추가 가능 → 여러 목표 동시 모니터링
- 위젯 탭 → 세부 화면으로 이동

### 목표 관리 (앱 메인)
- 목표 목록 + 달성률 바 표시
- 목표 추가 (FAB 버튼)
- 목표 삭제 (길게 누르기)

### 세부 항목 (DetailActivity)
- 목표별 세부 항목(글 + 수치) 추가/편집/삭제
- 각 항목의 현재수치와 목표수치로 개별 달성률 표시
- 전체 목표 달성률 = 전체 현재수치 합계 / 전체 목표수치 합계

## 🗂️ 프로젝트 구조

```
app/src/main/
├── java/com/goalwidget/
│   ├── Goal.kt                  # 데이터 모델 (Goal, GoalItem, GoalRepository)
│   ├── GoalWidgetProvider.kt    # AppWidgetProvider (위젯 렌더링)
│   ├── MainActivity.kt          # 목표 목록 화면
│   ├── DetailActivity.kt        # 세부 항목 관리 화면
│   └── WidgetConfigActivity.kt  # 위젯 추가 시 목표 선택 화면
├── res/
│   ├── layout/
│   │   ├── goal_widget.xml           # 위젯 레이아웃
│   │   ├── activity_main.xml         # 메인 액티비티
│   │   ├── activity_detail.xml       # 세부 화면
│   │   ├── activity_widget_config.xml # 위젯 설정
│   │   ├── dialog_create_goal.xml    # 목표 생성 다이얼로그
│   │   ├── dialog_item_edit.xml      # 항목 추가/편집 다이얼로그
│   │   ├── item_goal.xml             # 목표 목록 아이템
│   │   └── item_detail.xml           # 세부 항목 아이템
│   └── xml/
│       └── goal_widget_info.xml      # 위젯 메타데이터
```

## 🚀 빌드 방법

### 요구사항
- Android Studio Hedgehog (2023.1.1) 이상
- Android SDK 34
- Kotlin 1.9.0

### 빌드 단계
1. Android Studio에서 프로젝트 열기
2. Gradle Sync 실행
3. Run 또는 `./gradlew assembleDebug`

## 📲 위젯 사용법

1. 앱 실행 → **+** 버튼으로 목표 생성
2. 목표 탭 → 세부 항목 추가 (항목명 + 현재수치 + 목표수치)
3. 홈 화면 장기 탭 → 위젯 추가 → **목표 달성 위젯** 선택
4. 표시할 목표 선택 → 위젯 배치

## 🎨 데이터 저장
SharedPreferences (JSON via Gson)로 기기 로컬 저장

## ⚙️ 의존성
```
implementation 'com.google.code.gson:gson:2.10.1'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'com.google.android.material:material:1.11.0'
```
