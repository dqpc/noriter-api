# noriter-api

noriter 의 백엔드 API. 프론트는 별도 저장소 `noriter-web` (Vite + React, Cloudflare Pages).
학습 겸 운영 목적의 Spring Boot 프로젝트. 로그인·리더보드·댓글을 담당한다.

## 스택

- Java 21, Spring Boot 4.1, Gradle (Kotlin DSL)
- Spring Web MVC, Spring Security (+ OAuth2 Client), Spring Data JPA, Validation
- PostgreSQL 17, Flyway 로 스키마 관리 (`ddl-auto: validate`)
- 테스트는 H2(PostgreSQL 모드) + `test` 프로필. 외부 DB 불필요.
- 배포: Docker 이미지 → 서버컴, Cloudflare Tunnel 로 `api.<domain>` 노출 예정.

## 명령

```
docker compose up -d      # 로컬 Postgres (noriter/noriter, 5432)
./gradlew bootRun         # http://localhost:8080
./gradlew test
docker build -t noriter-api .
```

## 설정 (환경변수)

| 변수 | 기본값 | 용도 |
|---|---|---|
| DB_URL | jdbc:postgresql://localhost:5432/noriter | |
| DB_USER / DB_PASSWORD | noriter / noriter | |
| CORS_ALLOWED_ORIGINS | http://localhost:5173 | 쉼표 구분. 운영은 https://<domain> |
| PORT | 8080 | |

## 구조

```
src/main/java/games/noriter/api/
  NoriterApiApplication.java
  config/SecurityConfig.java     공개 경로: /actuator/health, /api/public/**. 나머지 인증.
src/main/resources/
  application.yml
  db/migration/V1__init.sql      app_user, game_score
```

## 규칙

- 스키마 변경은 반드시 Flyway 마이그레이션(`V<n>__<desc>.sql`)으로. 엔티티만 바꾸면 validate 에서 실패한다.
- SQL 은 H2 PostgreSQL 모드에서도 돌아가게 쓴다 (IDENTITY, TIMESTAMP WITH TIME ZONE 등). 테스트가 H2 로 돌기 때문.
- 공개 조회 API 는 `/api/public/**`, 인증 필요한 API 는 `/api/**` 아래에 둔다.
- `game_id` 는 프론트 `src/games/registry.ts` 의 id 문자열과 맞춘다 (예: "2048").
- 인증 방식(OAuth2 로그인 후 세션 vs JWT)은 아직 미정. 정해지면 여기에 기록.

## 다음 할 일

1. 소셜 로그인 (Google) → app_user 생성
2. 리더보드: `POST /api/scores`, `GET /api/public/leaderboard/{gameId}`
3. 댓글 테이블 + API
