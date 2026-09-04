#!/bin/sh
# 서버컴(ssh home)에 API 배포: 빌드 → jar 업로드 → launchd 서비스 재시작 → 헬스체크
set -e
cd "$(dirname "$0")/.."
./gradlew bootJar --no-daemon -q
scp -q build/libs/noriter-api-*.jar home:~/opt/noriter-api/app.jar.new
ssh home '
  set -e
  cd ~/opt/noriter-api && mv app.jar.new app.jar
  launchctl unload ~/Library/LaunchAgents/games.noriter.api.plist
  launchctl load -w ~/Library/LaunchAgents/games.noriter.api.plist
  for i in $(seq 1 60); do curl -sf http://127.0.0.1:8080/actuator/health >/dev/null && break; sleep 2; done
  curl -s http://127.0.0.1:8080/actuator/health; echo
'
