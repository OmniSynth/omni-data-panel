/** 登录挑战与 HMAC-SHA256 签名（与后端 LoginChallengeService 约定一致）。 */

export type LoginChallenge = {
  challengeId: string
  nonce: string
  timestamp: number
  expiresAt: number
  signKey: string
}

export type SignedLoginPayload = {
  username: string
  password: string
  challengeId: string
  nonce: string
  timestamp: number
  signature: string
}

function toHex(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  return [...view].map((byte) => byte.toString(16).padStart(2, '0')).join('')
}

function parseHexKey(hex: string): Uint8Array {
  const normalized = hex.trim().toLowerCase()
  if (normalized.length % 2 !== 0) {
    throw new Error('登录签名密钥无效')
  }
  const bytes = new Uint8Array(normalized.length / 2)
  for (let i = 0; i < bytes.length; i += 1) {
    bytes[i] = Number.parseInt(normalized.slice(i * 2, i * 2 + 2), 16)
  }
  return bytes
}

/** 局域网 HTTP 等非安全上下文没有 crypto.subtle，需纯 JS 回退。 */
function subtleCrypto(): SubtleCrypto | undefined {
  return globalThis.crypto?.subtle
}

async function hmacSha256Hex(keyBytes: Uint8Array, message: string): Promise<string> {
  const data = new TextEncoder().encode(message)
  const subtle = subtleCrypto()
  if (subtle) {
    const key = await subtle.importKey(
      'raw',
      keyBytes.buffer.slice(keyBytes.byteOffset, keyBytes.byteOffset + keyBytes.byteLength) as ArrayBuffer,
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign'],
    )
    return toHex(await subtle.sign('HMAC', key, data))
  }
  return toHex(hmacSha256(keyBytes, data))
}

/** SHA-256（FIPS 180-4），仅作非安全上下文下的登录签名回退。 */
function sha256(message: Uint8Array): Uint8Array {
  const K = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  ])
  const bitLen = message.length * 8
  const withPad = new Uint8Array(((message.length + 9 + 63) & ~63))
  withPad.set(message)
  withPad[message.length] = 0x80
  const view = new DataView(withPad.buffer)
  view.setUint32(withPad.length - 4, bitLen >>> 0, false)
  view.setUint32(withPad.length - 8, Math.floor(bitLen / 0x100000000), false)

  let h0 = 0x6a09e667
  let h1 = 0xbb67ae85
  let h2 = 0x3c6ef372
  let h3 = 0xa54ff53a
  let h4 = 0x510e527f
  let h5 = 0x9b05688c
  let h6 = 0x1f83d9ab
  let h7 = 0x5be0cd19
  const w = new Uint32Array(64)
  const rotr = (x: number, n: number) => (x >>> n) | (x << (32 - n))

  for (let offset = 0; offset < withPad.length; offset += 64) {
    for (let i = 0; i < 16; i += 1) {
      w[i] = view.getUint32(offset + i * 4, false)
    }
    for (let i = 16; i < 64; i += 1) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3)
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10)
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0
    }
    let a = h0
    let b = h1
    let c = h2
    let d = h3
    let e = h4
    let f = h5
    let g = h6
    let h = h7
    for (let i = 0; i < 64; i += 1) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
      const ch = (e & f) ^ (~e & g)
      const temp1 = (h + S1 + ch + K[i] + w[i]) >>> 0
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const temp2 = (S0 + maj) >>> 0
      h = g
      g = f
      f = e
      e = (d + temp1) >>> 0
      d = c
      c = b
      b = a
      a = (temp1 + temp2) >>> 0
    }
    h0 = (h0 + a) >>> 0
    h1 = (h1 + b) >>> 0
    h2 = (h2 + c) >>> 0
    h3 = (h3 + d) >>> 0
    h4 = (h4 + e) >>> 0
    h5 = (h5 + f) >>> 0
    h6 = (h6 + g) >>> 0
    h7 = (h7 + h) >>> 0
  }

  const out = new Uint8Array(32)
  const outView = new DataView(out.buffer)
  outView.setUint32(0, h0, false)
  outView.setUint32(4, h1, false)
  outView.setUint32(8, h2, false)
  outView.setUint32(12, h3, false)
  outView.setUint32(16, h4, false)
  outView.setUint32(20, h5, false)
  outView.setUint32(24, h6, false)
  outView.setUint32(28, h7, false)
  return out
}

function hmacSha256(keyBytes: Uint8Array, message: Uint8Array): Uint8Array {
  const block = 64
  let key = keyBytes
  if (key.length > block) {
    key = sha256(key)
  }
  if (key.length < block) {
    const padded = new Uint8Array(block)
    padded.set(key)
    key = padded
  }
  const oKey = new Uint8Array(block)
  const iKey = new Uint8Array(block)
  for (let i = 0; i < block; i += 1) {
    oKey[i] = key[i] ^ 0x5c
    iKey[i] = key[i] ^ 0x36
  }
  const inner = new Uint8Array(block + message.length)
  inner.set(iKey)
  inner.set(message, block)
  const outer = new Uint8Array(block + 32)
  outer.set(oKey)
  outer.set(sha256(inner), block)
  return sha256(outer)
}

/**
 * 使用挑战密钥对登录载荷计算 HMAC-SHA256。
 */
export async function signLogin(
  challenge: LoginChallenge,
  username: string,
  password: string,
): Promise<SignedLoginPayload> {
  const timestamp = Math.floor(Date.now() / 1000)
  const payload = `${username}\n${password}\n${challenge.nonce}\n${timestamp}`
  const signature = await hmacSha256Hex(parseHexKey(challenge.signKey), payload)
  return {
    username,
    password,
    challengeId: challenge.challengeId,
    nonce: challenge.nonce,
    timestamp,
    signature,
  }
}
