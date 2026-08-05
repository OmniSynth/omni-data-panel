import crypto from 'node:crypto'

const API = 'http://127.0.0.1:8080/api'

async function login() {
  const ch = await fetch(`${API}/auth/login-challenge`).then((r) => r.json())
  const d = ch.data
  const u = 'admin'
  const p = 'admin123'
  const ts = Math.floor(Date.now() / 1000)
  const sig = crypto.createHmac('sha256', Buffer.from(d.signKey, 'hex'))
    .update(`${u}\n${p}\n${d.nonce}\n${ts}`)
    .digest('hex')
  const login = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: u, password: p, challengeId: d.challengeId, nonce: d.nonce, timestamp: ts, signature: sig,
    }),
  }).then((r) => r.json())
  return login.data.accessToken
}

async function q(token, body) {
  const h = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const sub = await fetch(`${API}/queries`, { method: 'POST', headers: h, body: JSON.stringify(body) }).then((r) => r.json())
  if (sub.code !== 0) return sub
  const qid = sub.data.queryId
  for (let i = 0; i < 40; i++) {
    await new Promise((r) => setTimeout(r, 300))
    const st = await fetch(`${API}/queries/${qid}`, { headers: h }).then((r) => r.json())
    if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(st.data?.status)) return st.data
  }
  return { status: 'TIMEOUT' }
}

const token = await login()
const src = '2084177715329564673'
for (const [name, sql] of [
  ['select1', 'SELECT 1 AS x'],
  ['db', 'SELECT DATABASE() AS db'],
  ['show', "SHOW TABLES FROM big_data LIKE 'sys_api_log'"],
  ['count', 'SELECT COUNT(*) AS c FROM big_data.sys_api_log'],
  ['limit', 'SELECT id, caller FROM big_data.sys_api_log LIMIT 3'],
]) {
  console.log(name, JSON.stringify(await q(token, { sourceId: src, sql, parameters: [] })))
}
