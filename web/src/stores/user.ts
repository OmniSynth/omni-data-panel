import { defineStore } from 'pinia'
import { authApi, TOKEN_KEY } from '@/api'
import type { User } from '@/types'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null as User | null,
  }),
  getters: {
    roleCodes: (state) => state.user?.roles || [],
    isAdmin(): boolean {
      return this.roleCodes.includes('ADMIN')
    },
  },
  actions: {
    async login(username: string, password: string) {
      const { accessToken } = await authApi.login({ username, password })
      this.token = accessToken
      localStorage.setItem(TOKEN_KEY, accessToken)
      await this.loadUser()
    },
    async loadUser() {
      this.user = await authApi.me()
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
    },
    hasPermission(permission: string) {
      return this.isAdmin || this.user?.permissions.includes(permission) || false
    },
  },
})
