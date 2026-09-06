# noriter-api

놀이터(noriter)의 백엔드. 방 대전, 채팅, 리더보드를 담당한다. 프론트는 [noriter-web](https://github.com/dqpc/noriter-web).

## 기술 스택

**Spring Boot 4.1 위의 Java 25 모놀리스다.** Web MVC 가 REST(방 생성·조회, 리더보드)를, Spring WebSocket 이 방 안 실시간 통신(입장·설정·시작·점수·채팅·상태 중계)을 맡는다. WebSocket 메시지는 `type` 필드로 구분하는 JSON 이고, 서버 쪽에서는 sealed interface + record 로 정의해 switch 패턴 매칭으로 처리한다. `spring.threads.virtual.enabled` 로 버추얼 스레드를 켜서, 요청과 WebSocket 처리 스레드가 블로킹되어도 플랫폼 스레드를 점유하지 않는다.

**모듈 경계는 Spring Modulith 가 지킨다.** `games.noriter.api` 아래 패키지 하나가 모듈이고(`game`, `user`, `score`, `room`), 모듈 루트의 서비스·읽기 모델만 다른 모듈이 참조할 수 있다. 하위 패키지(`domain`, `infra`, `web`)는 모듈 내부라서 잘못 참조하면 `ModularityTests` 가 실패한다. 모듈 간 부수효과는 `ApplicationEventPublisher` 이벤트로 넘기고, 이벤트 발행 기록은 Modulith 의 JDBC 레지스트리가 남긴다. 나중에 서버를 쪼갤 일이 생기면 이 경계를 그대로 잘라내는 것이 목표다.

**게임 확장은 데이터로 한다.** `GameSpec`(인원 범위, 제한 시간, seed 사용 여부, 점수 방향, 옵션 선택지)을 `GameCatalog` 에 한 줄 등록하면 방·대전·순위가 그 선언만 보고 동작한다. 실시간 경쟁 게임(2048·계단)은 규칙이 클라이언트에 있고 서버는 점수와 상태를 중계만 한다. 턴제 게임(윷놀이)은 `TurnGame` 구현체가 서버에서 난수·판정·봇을 맡고 클라이언트는 의도(`action`)만 보내므로 콘솔 조작으로 수를 만들 수 없다. 방 상태와 채팅은 메모리(`InMemoryRoomRepository`)에만 있어 재시작하면 사라진다. 제한 시간 종료와 카운트다운은 `TaskScheduler` 로 예약한다.

**저장소는 PostgreSQL 18 이고 스키마는 Flyway 가 관리한다.** JPA 는 `ddl-auto: validate` 로만 쓰고 변경은 항상 마이그레이션 파일로 한다. 테이블·컬럼은 PostgreSQL 관례대로 snake_case 소문자·단수형(`app_user.provider_id`)이고 Java 필드는 camelCase 그대로 Spring 기본 네이밍 전략이 변환한다. 제약·인덱스는 `pk_`/`uk_`/`fk_`/`ix_` 접두사. 삭제는 물리 삭제 대신 `deleted_at` 을 채우는 소프트 삭제(Hibernate `@SoftDelete`)다. 테스트는 H2 를 PostgreSQL 모드로 띄워 외부 DB 없이 돌고, 그래서 마이그레이션 SQL 은 두 DB 에서 다 도는 문법만 쓴다. Spring Security 는 공개 경로(방·리더보드 조회, WebSocket, 헬스체크)와 인증 필요 경로를 메서드+경로 매처로 나누며, 로그인은 JWT 로 붙일 예정이다.

**의존성 주입은 Lombok `@RequiredArgsConstructor`** 로 하고 설정값은 `@ConfigurationProperties` record(`NoriterProperties`)로 받는다. 빌드는 Gradle(Kotlin DSL), 테스트는 JUnit 5 + AssertJ 이며 WebSocket 은 실제 서버를 띄워 클라이언트 두 개로 검증한다.

**배포는 집 서버컴에서 self-hosted runner 가 한다.** GitHub Actions 가 서버컴(Ubuntu Server)에서 테스트·bootJar 후 systemd 서비스를 재시작하고, dev(8081)와 prod(8080) 인스턴스가 각자 DB 를 갖는다. 외부 공개는 도메인 구매 전까지 Cloudflare Worker 프록시(`edge/api-proxy`)가 KV 에 기록된 Quick Tunnel 주소로 요청을 넘기는 임시 구성이다.

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

프로파일로 환경을 나눈다. 지정하지 않으면 `local`.

| 프로파일 | DB | 포트 | CORS | dev 옵션 |
|---|---|---|---|---|
| local (기본) | localhost/noriter | 8080 | localhost:5173 | on |
| dev | 127.0.0.1/noriter_dev | 8081 | dev 사이트 + localhost | on |
| prod | 127.0.0.1/noriter | 8080 | prod 사이트 | off |

`SPRING_PROFILES_ACTIVE=dev` 처럼 고르고, 필요하면 `DB_URL` / `DB_USER` / `DB_PASSWORD` / `PORT` / `CORS_ALLOWED_ORIGINS` 환경변수로 덮어쓴다.

## API

REST

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/rooms | 방 생성 `{gameId}` → 방 스냅샷 (id 가 초대 코드) |
| GET | /api/rooms/{id} | 방 조회 |
| GET | /api/games/{gameId}/leaderboard?limit= | 리더보드 (점수 제출 API 는 로그인 후) |
| POST | /api/visits | 방문 1건 기록 → `{today, total}` (프론트가 브라우저·일 단위로 한 번) |
| GET | /api/visits | 방문 통계 |
| GET | /actuator/health | 헬스체크 |

WebSocket `/ws/rooms/{id}` — JSON, `type` 필드로 구분

| 방향 | type | 내용 |
|---|---|---|
| → | join | `{nickname, character, playerId?}` 입장. 첫 입장자가 방장. `playerId` 는 브라우저가 보관하는 토큰(16~64자)으로, 같은 토큰으로 다시 join 하면 진행 중이던 자리로 복귀 |
| → | settings | `{maxPlayers?, options?}` 방장만, 대기 중에만 |
| → | character | `{character}` 내 캐릭터 변경 |
| → | start | 방장. 3초 카운트다운 후 시작, seed 배포 |
| → | score / finish | `{score}` 점수 갱신 / 종료 |
| → | state | 게임 상태(형식 자유). 다른 참가자에게 그대로 중계 |
| → | chat | `{text}` 200자 |
| → | rematch | 방장, 종료 후. 점수 초기화하고 바로 카운트다운 |
| → | ping | 30초마다. 프록시 유휴 종료 방지 |
| → | action | 턴제 게임의 수. 윷놀이: `{type:"throw", result?}`(골라 던지기 카드일 때 result), `{type:"move", pieceId, result, steps?, via?}`, `{type:"card", index}`, `{type:"surrender"}` |
| ← | hello | `{playerId}` join 직후. 토큰을 보냈으면 그 값 |
| ← | room | 방 스냅샷 (상태·참가자(`connected` 포함)·설정·seed·시각) + `serverTime`. 변경마다 전원. 클라이언트는 serverTime 으로 시계 차이를 보정 |
| ← | playerState | `{playerId, state}` 다른 참가자의 게임 상태 |
| ← | gameState | 턴제 게임의 판 전체(차례·단계·결과 큐·말 위치·가능한 수·순위) + `serverTime`. 서버 판정 결과 |
| ← | chat / chatHistory | 채팅, 입장 시 최근 50개 |
| ← | error / pong | |

방 상태와 채팅은 메모리에만 있다. 대기·종료 중에 나가면 방에서 빠지고, 진행 중에 연결이 끊기면 자리를 남겨 둔다(턴제는 봇이 대신). 전원 끊긴 채 60초가 지나거나 방이 비면 사라진다.

## 구조

모듈러 모놀리스. `games.noriter.api` 아래 패키지 하나가 모듈 하나이고, 경계는 테스트(`ModularityTests`)가 검증한다.

```
config/   보안, 스케줄러, 설정 프로퍼티
game/     GameSpec 레지스트리 (인원 범위·제한시간·seed·옵션·turnBased). TurnGame 인터페이스
  yut/    윷놀이 규칙·봇 (29칸 경로, 지름길, 빽도, 잡기·업기, 턴 30초), 천사·악마 카드(잡기·방 도착·시작 때 천사 4 + 악마 1 더미에서 한 장, 15초), 항복
user/     계정 (로그인 예정)
score/    점수·리더보드
room/     방·대전·채팅   domain/ Room  infra/ 메모리 저장소·WebSocket 세션  web/ 컨트롤러·핸들러·DTO
visit/    방문자 수 (site_visit 일별 카운트, Asia/Seoul)
```

모듈 루트에는 다른 모듈에 공개하는 서비스와 읽기 모델만 두고, `domain` / `infra` / `web` 으로 나눈다. 2048·계단 규칙은 서버에 없고 점수와 상태를 중계만 한다. 윷놀이처럼 판이 하나인 턴제 게임은 `TurnGame` 구현체가 서버에서 판정하며, 방은 `deadline` 시각에 `auto` 를 예약해 시간 초과와 봇 차례를 처리한다.
