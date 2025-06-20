# 🎯 DSM 멸망전 팀 구성 플랫폼

학생 인증과 Riot API 연동을 통해 사용자의 롤 티어 기반 점수로 팀을 만들고, 영입 시스템을 통해 멸망전 팀을 구성하는 웹 서비스입니다.

## 🚀 주요 기능

### ✅ 회원가입 및 로그인
- **학교 OAuth 로그인**: DSM 학교 계정을 통한 안전한 인증
- **Riot 계정 연동**: 소환사명을 통한 League of Legends 계정 연결
- **티어 기반 점수 계산**: 티어와 LP를 기반으로 한 정확한 점수 산정

### ✅ 팀 관리
- **팀 생성**: 사용자당 1개의 팀 생성 가능
- **팀원 관리**: 최대 5명까지 팀 구성
- **팀 점수**: 모든 팀원의 점수 합계로 팀 순위 결정

### ✅ 영입 시스템
- **영입 요청**: 팀장이 마음에 드는 유저에게 영입 신청
- **요청 관리**: 수락/거절 시스템
- **자동 팀 이동**: 수락 시 기존 팀에서 자동 탈퇴 후 새 팀 합류

### ✅ 사용자 관리
- **유저 목록**: 모든 사용자 조회 및 필터링
- **팀 현황**: 팀 소속 여부 및 역할 확인
- **점수 랭킹**: 개인 및 팀 점수 기반 순위

## 🛠 기술 스택

### Backend
- **Language**: Kotlin
- **Framework**: Spring Boot 3.5.0
- **Database**: MySQL 8.0
- **ORM**: Spring Data JPA
- **Security**: Spring Security + JWT
- **API Client**: WebClient (Reactive)

### Frontend
- **Template Engine**: Thymeleaf
- **CSS Framework**: Bootstrap 5.1.3
- **Icons**: Font Awesome 6.0.0
- **JavaScript**: Vanilla JS (ES6+)

### External APIs
- **Riot Games API**: League of Legends 데이터 연동
- **DSM OAuth API**: 학교 계정 인증

## 📋 사전 요구사항

- Java 17 이상
- MySQL 8.0 이상
- Riot Games API 키
- DSM OAuth 클라이언트 정보

## ⚙️ 설정 방법

### 1. 데이터베이스 설정

MySQL에 데이터베이스를 생성합니다:

```sql
CREATE DATABASE dsm_lol_league CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. application.properties 수정

`src/main/resources/application.properties` 파일의 다음 설정을 수정하세요:

```properties
# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/dsm_lol_league?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD

# Riot API Configuration
riot.api.key=YOUR_RIOT_API_KEY

# School OAuth Configuration (실제 값으로 교체 필요)
school.oauth.client-id=YOUR_DSM_CLIENT_ID
school.oauth.client-secret=YOUR_DSM_CLIENT_SECRET
```

### 3. Riot Games API 키 발급

1. [Riot Developer Portal](https://developer.riotgames.com/)에 접속
2. 계정 로그인 후 API Key 발급
3. Development API Key를 `riot.api.key`에 설정

## 🏃‍♂️ 실행 방법

### Gradle을 사용한 실행

```bash
# 프로젝트 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

### IDE에서 실행

1. IntelliJ IDEA 또는 Eclipse에서 프로젝트 열기
2. `DsmLolLeagueApplication.kt` 파일 실행
3. 브라우저에서 `http://localhost:8080` 접속

## 📱 사용 방법

### 1. 회원가입 및 로그인
1. 메인 페이지에서 "시작하기" 클릭
2. DSM 학교 계정으로 로그인
3. 소환사명 입력하여 Riot 계정 연동

### 2. 팀 생성
1. 대시보드에서 "팀 생성" 클릭
2. 팀 이름 입력 (고유해야 함)
3. 자동으로 팀장이 되어 팀 관리 가능

### 3. 팀원 영입
1. "유저 목록" 페이지에서 영입하고 싶은 유저 찾기
2. "영입 신청" 버튼 클릭
3. 상대방이 수락하면 팀에 합류

### 4. 영입 요청 관리
1. "영입 관리" 페이지에서 받은 요청 확인
2. 수락 또는 거절 선택
3. 수락 시 기존 팀에서 자동 탈퇴 후 새 팀 합류

## 🏗 프로젝트 구조

```
src/main/kotlin/com/dsm/dsmlolleague/
├── controller/          # REST API 컨트롤러
│   ├── UserController.kt
│   ├── TeamController.kt
│   ├── RecruitController.kt
│   └── WebController.kt
├── service/            # 비즈니스 로직
│   ├── UserService.kt
│   ├── TeamService.kt
│   ├── RecruitService.kt
│   ├── RiotApiService.kt
│   ├── SchoolOAuthService.kt
│   └── JwtTokenService.kt
├── repository/         # 데이터 접근 계층
│   ├── UserRepository.kt
│   ├── TeamRepository.kt
│   └── RecruitRequestRepository.kt
├── entity/            # JPA 엔티티
│   ├── User.kt
│   ├── Team.kt
│   └── RecruitRequest.kt
├── dto/               # 데이터 전송 객체
│   ├── UserDto.kt
│   ├── TeamDto.kt
│   ├── RecruitDto.kt
│   └── ApiResponse.kt
└── DsmLolLeagueApplication.kt

src/main/resources/
├── templates/         # Thymeleaf 템플릿
│   ├── layout.html
│   ├── index.html
│   ├── login.html
│   └── ...
└── application.properties
```

## 🎯 점수 계산 방식

```kotlin
val tierScoreMap = mapOf(
    Tier.IRON to 500,
    Tier.BRONZE to 700,
    Tier.SILVER to 900,
    Tier.GOLD to 1100,
    Tier.PLATINUM to 1300,
    Tier.EMERALD to 1500,
    Tier.DIAMOND to 1700,
    Tier.MASTER to 2000,
    Tier.GRANDMASTER to 2200,
    Tier.CHALLENGER to 2500
)

// 최종 점수 = 티어 기본 점수 + LP
score = tierScoreMap[tier] + leaguePoints
```

## 🔧 주요 API 엔드포인트

### 사용자 관련
- `POST /api/users/login` - 로그인
- `POST /api/users/register-riot` - Riot 계정 연동
- `GET /api/users/me` - 내 정보 조회
- `GET /api/users` - 전체 사용자 조회

### 팀 관련
- `POST /api/teams` - 팀 생성
- `GET /api/teams` - 전체 팀 조회
- `GET /api/teams/my-team` - 내 팀 조회
- `POST /api/teams/leave` - 팀 탈퇴

### 영입 관련
- `POST /api/recruits` - 영입 요청 전송
- `POST /api/recruits/{id}/respond` - 영입 요청 응답
- `GET /api/recruits/pending` - 받은 영입 요청 조회

## 🚨 예외 처리

- 소환사명 중복 입력 시 경고
- Riot API 실패 시 에러 메시지 표시
- 영입 신청 중복 방지
- 팀 인원 5명 초과 방지
- 유저당 팀 하나만 소속 가능

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 있습니다.

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📞 문의사항

프로젝트에 대한 문의사항이 있으시면 Issue를 생성해주세요. 