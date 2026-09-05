# noriter-api

놀이터(noriter)의 백엔드. 방 대전, 채팅, 리더보드를 담당한다. 프론트는 [noriter-web](https://github.com/dqpc/noriter-web).

## 스택

- Java 25, Spring Boot 4.1, Gradle (Kotlin DSL), 버추얼 스레드
- Spring Web MVC + WebSocket, Spring Security, Spring Data JPA, Spring Modulith
- PostgreSQL 18, Flyway. 테이블·컬럼은 camelCase
- 테스트는 H2 (PostgreSQL 모드), 외부 DB 없이 `./gradlew test`

## 주소

| 환경 | 브랜치 | API |
|---|---|---|
| prod | main | https://noriter-api.asgd56.workers.dev |
| dev | develop | https://noriter-api-dev.asgd56.workers.dev |

집 서버컴에서 두 인스턴스가 돌고, GitHub Actions self-hosted runner 가 브랜치 push 마다 재배포한다. 외부 공개는 Cloudflare Worker 프록시 + Quick Tunnel (도메인 구매 전 임시).

## 실행

```
docker compose up -d      # 로컬 PostgreSQL (Docker 있을 때)
./gradlew bootRun         # http://localhost:8080
./gradlew test
```

| 환경변수 | 기본값 |
|---|---|
| DB_URL / DB_USER / DB_PASSWORD | localhost noriter / noriter / noriter |
| CORS_ALLOWED_ORIGINS | http://localhost:5173 (쉼표 구분) |
| PORT | 8080 |
| NORITER_DEV_OPTIONS | false (true 면 dev 전용 옵션 추가) |

## API

REST

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/rooms | 방 생성 `{gameId}` → 방 스냅샷 (id 가 초대 코드) |
| GET | /api/rooms/{id} | 방 조회 |
| GET | /api/games/{gameId}/leaderboard?limit= | 리더보드 (점수 제출 API 는 로그인 후) |
| GET | /actuator/health | 헬스체크 |

WebSocket `/ws/rooms/{id}` — JSON, `type` 필드로 구분

| 방향 | type | 내용 |
|---|---|---|
| → | join | `{nickname, character}` 입장. 첫 입장자가 방장 |
| → | settings | `{maxPlayers?, options?}` 방장만, 대기 중에만 |
| → | character | `{character}` 내 캐릭터 변경 |
| → | start | 방장. 3초 카운트다운 후 시작, seed 배포 |
| → | score / finish | `{score}` 점수 갱신 / 종료 |
| → | state | 게임 상태(형식 자유). 다른 참가자에게 그대로 중계 |
| → | chat | `{text}` 200자 |
| → | rematch | 방장, 종료 후. 점수 초기화하고 바로 카운트다운 |
| → | ping | 30초마다. 프록시 유휴 종료 방지 |
| ← | hello | `{playerId}` |
| ← | room | 방 스냅샷 (상태·참가자·설정·seed·시각). 변경마다 전원 |
| ← | playerState | `{playerId, state}` 다른 참가자의 게임 상태 |
| ← | chat / chatHistory | 채팅, 입장 시 최근 50개 |
| ← | error / pong | |

방 상태와 채팅은 메모리에만 있다. 방이 비면 사라진다.

## 구조

모듈러 모놀리스. `games.noriter.api` 아래 패키지 하나가 모듈 하나이고, 경계는 테스트(`ModularityTests`)가 검증한다.

```
config/   보안, 스케줄러, 설정 프로퍼티
game/     GameSpec 레지스트리 (인원 범위·제한시간·seed·옵션). 새 게임은 한 줄 추가
user/     계정 (로그인 예정)
score/    점수·리더보드
room/     방·대전·채팅   domain/ Room  infra/ 메모리 저장소·WebSocket 세션  web/ 컨트롤러·핸들러·DTO
```

모듈 루트에는 다른 모듈에 공개하는 서비스와 읽기 모델만 두고, `domain` / `infra` / `web` 으로 나눈다. 게임 규칙은 서버에 없다. 서버는 게임 종류를 모른 채 점수와 상태를 중계한다.
