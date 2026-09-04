# api-proxy

도메인 구매 전 임시 구성. `noriter-api-dev` / `noriter-api` Worker 가 KV `noriter-origins` 의 `dev` / `prod` 키에 저장된
Quick Tunnel 주소로 요청(WebSocket 포함)을 전달한다. 서버컴의 터널 래퍼가 터널을 열 때마다 KV 를 갱신한다.

```
npx wrangler deploy              # dev
npx wrangler deploy --env prod   # prod
```
