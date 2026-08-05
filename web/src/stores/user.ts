import { defineStore } from 'pinia'
import { signLogin } from '@/auth/loginSignature'
import { authApi, TOKEN_KEY } from '@/api'
import type { User } from '@/types'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null as User | null,
    mfaToken: '' as string,
  }),
  getters: {
    roleCodes: (state) => state.user?.roles || [],
    isAdmin(): boolean {
      return this.roleCodes.includes('ADMIN')
    },
    mfaRequired(): boolean {
      return !!this.mfaToken
    },
  },
  actions: {
    async login(username: string, password: string): Promise<'ok' | 'mfa'> {
      const challenge = await authApi.loginChallenge()
      const signed = await signLogin(challenge, username, password)
      const result = await authApi.login(signed)
      if (result.mfaRequired && result.mfaToken) {
        this.mfaToken = result.mfaToken
        return 'mfa'
      }
      if (!result.accessToken) {
        throw new Error('登录响应无效')
      }
      this.mfaToken = ''
      this.token = result.accessToken
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      await this.loadUser()
      return 'ok'
    },
    async verifyMfa(code: string) {
      if (!this.mfaToken) {
        throw new Error('MFA 会话已失效，请重新登录')
      }
      const result = await authApi.verifyMfa({ mfaToken: this.mfaToken, code })
      if (!result.accessToken) {
        throw new Error('登录响应无效')
      }
      this.mfaToken = ''
      this.token = result.accessToken
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      await this.loadUser()
    },
    clearMfaChallenge() {
      this.mfaToken = ''
    },
    async loadUser() {
      this.user = await authApi.me()
    },
    logout() {
      this.token = ''
      this.user = null
      this.mfaToken = ''
      localStorage.removeItem(TOKEN_KEY)
    },
    hasPermission(permission: string) {
      return this.isAdmin || this.user?.permissions.includes(permission) || false
    },
  },
})
