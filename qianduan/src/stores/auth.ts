import { defineStore } from 'pinia'

export type UserRole = 'ROLE1' | 'ROLE2' | 'ROLE3'

interface AuthState {
  token: string
  role: UserRole | ''
  username: string
  realName: string
  userId: number | null
  localAccount: boolean
}

const TOKEN_KEY = 'AUTH_TOKEN'
const ROLE_KEY = 'AUTH_ROLE'
const USERNAME_KEY = 'AUTH_USERNAME'
const REALNAME_KEY = 'AUTH_REALNAME'
const USER_ID_KEY = 'AUTH_USER_ID'
const LOCAL_ACCOUNT_KEY = 'AUTH_LOCAL_ACCOUNT'

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    role: (localStorage.getItem(ROLE_KEY) as UserRole | '') || '',
    username: localStorage.getItem(USERNAME_KEY) || '',
    realName: localStorage.getItem(REALNAME_KEY) || '',
    userId: localStorage.getItem(USER_ID_KEY)
      ? Number(localStorage.getItem(USER_ID_KEY))
      : null,
    localAccount: localStorage.getItem(LOCAL_ACCOUNT_KEY) === 'true'
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isRole1: (state) => state.role === 'ROLE1',
    isRole2: (state) => state.role === 'ROLE2',
    isRole3: (state) => state.role === 'ROLE3'
  },

  actions: {
    setAuth(payload: {
      token: string
      role: UserRole
      username: string
      realName: string
      userId: number
      localAccount: boolean
    }) {
      this.token = payload.token
      this.role = payload.role
      this.username = payload.username
      this.realName = payload.realName
      this.userId = payload.userId
      this.localAccount = payload.localAccount

      localStorage.setItem(TOKEN_KEY, payload.token)
      localStorage.setItem(ROLE_KEY, payload.role)
      localStorage.setItem(USERNAME_KEY, payload.username)
      localStorage.setItem(REALNAME_KEY, payload.realName)
      localStorage.setItem(USER_ID_KEY, String(payload.userId))
      localStorage.setItem(LOCAL_ACCOUNT_KEY, String(payload.localAccount))
    },

    clearToken() {
      this.token = ''
      this.role = ''
      this.username = ''
      this.realName = ''
      this.userId = null
      this.localAccount = false

      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(ROLE_KEY)
      localStorage.removeItem(USERNAME_KEY)
      localStorage.removeItem(REALNAME_KEY)
      localStorage.removeItem(USER_ID_KEY)
      localStorage.removeItem(LOCAL_ACCOUNT_KEY)
    }
  }
})
