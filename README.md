# noriter-api

noriter 웹 놀이터의 백엔드 API (Spring Boot 4, Java 21, PostgreSQL).

## 실행

```
docker compose up -d
./gradlew bootRun
```

헬스체크: http://localhost:8080/actuator/health

## 개발 메모

구조와 규칙은 [CLAUDE.md](./CLAUDE.md) 참고. 프론트엔드는 [noriter-web](https://github.com/dqpc/noriter-web).
