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

function toHex(buffer: ArrayBuffer): string {
  return [...new Uint8Array(buffer)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
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
  const key = await crypto.subtle.importKey(
    'raw',
    parseHexKey(challenge.signKey),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signature = toHex(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(payload)))
  return {
    username,
    password,
    challengeId: challenge.challengeId,
    nonce: challenge.nonce,
    timestamp,
    signature,
  }
}
