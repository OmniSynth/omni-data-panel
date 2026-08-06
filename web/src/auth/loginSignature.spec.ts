import { createHmac } from 'node:crypto'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { signLogin, type LoginChallenge } from '@/auth/loginSignature'

describe('signLogin', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('对固定挑战生成与 Node HMAC 一致的签名', async () => {
    const nowMs = 1_700_000_000_000
    vi.spyOn(Date, 'now').mockReturnValue(nowMs)
    const signKey = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
    const challenge: LoginChallenge = {
      challengeId: 'c1',
      nonce: 'n1',
      timestamp: 1_700_000_000,
      expiresAt: 1_700_000_120,
      signKey,
    }
    const signed = await signLogin(challenge, 'alice', 'secret')
    const timestamp = Math.floor(nowMs / 1000)
    const expected = createHmac('sha256', Buffer.from(signKey, 'hex'))
      .update(`alice\nsecret\nn1\n${timestamp}`, 'utf8')
      .digest('hex')

    expect(signed).toEqual({
      username: 'alice',
      password: 'secret',
      challengeId: 'c1',
      nonce: 'n1',
      timestamp,
      signature: expected,
    })
  })

  it('签名密钥长度非法时抛错', async () => {
    const challenge: LoginChallenge = {
      challengeId: 'c1',
      nonce: 'n1',
      timestamp: 1,
      expiresAt: 2,
      signKey: 'abc',
    }
    await expect(signLogin(challenge, 'u', 'p')).rejects.toThrow('登录签名密钥无效')
  })
})
