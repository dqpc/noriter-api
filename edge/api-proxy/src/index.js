// 도메인 없이 API 고정 주소를 제공하는 프록시. 서버컴의 Quick Tunnel 주소를 KV(ORIGINS[ORIGIN_KEY]) 에서 읽어 전달한다.
export default {
  async fetch(request, env) {
    const origin = await env.ORIGINS.get(env.ORIGIN_KEY)
    if (!origin) return new Response('api origin not set', { status: 503 })
    const url = new URL(request.url)
    const target = new URL(url.pathname + url.search, origin)
    const headers = new Headers(request.headers)
    headers.set('Host', target.host)
    return fetch(target, { method: request.method, headers, body: request.body, redirect: 'manual' })
  },
}
