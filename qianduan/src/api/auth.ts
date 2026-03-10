import request from './request'
import { USE_REAL_API } from '../config/env'
import type { UserRole } from '../stores/auth'

export interface LocalLoginParams {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  role: UserRole
  username: string
  realName: string
  userId: number
  localAccount: boolean
}

export interface OauthCallbackParams {
  providerId: string
  code: string
  state: string
  redirectPath: string
}

export type OauthLoginResult = LoginResult

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

function buildMockLocalUser(username: string, password: string): LoginResult {
  if (password !== '123456') {
    throw new Error('用户名或密码错误')
  }

  if (username === 'admin') {
    return {
      token: 'mock-token-role3',
      role: 'ROLE3',
      username: 'admin',
      realName: '系统管理员',
      userId: 1,
      localAccount: true
    }
  }

  if (username === 'teacher01') {
    return {
      token: 'mock-token-role2',
      role: 'ROLE2',
      username: 'teacher01',
      realName: '张老师',
      userId: 2,
      localAccount: true
    }
  }

  if (username === 'student01') {
    return {
      token: 'mock-token-role1',
      role: 'ROLE1',
      username: 'student01',
      realName: '王同学',
      userId: 4,
      localAccount: true
    }
  }

  throw new Error('用户名或密码错误')
}

export async function localLogin(
  params: LocalLoginParams
): Promise<ApiResponse<LoginResult>> {
  if (USE_REAL_API) {
    return request.post('/auth/local/login', params)
  }

  return {
    code: 20000,
    message: 'success',
    data: buildMockLocalUser(params.username.trim(), params.password)
  }
}

function buildMockOauthUser(redirectPath: string): OauthLoginResult {
  if (redirectPath.startsWith('/admin')) {
    return {
      token: 'mock-oauth-token-role3',
      role: 'ROLE3',
      username: 'oauth_admin',
      realName: '第三方管理员',
      userId: 1,
      localAccount: false
    }
  }

  return {
    token: 'mock-oauth-token-role1',
    role: 'ROLE1',
    username: 'oauth_user',
    realName: '第三方用户',
    userId: 4,
    localAccount: false
  }
}

export async function oauthCallbackLogin(
  params: OauthCallbackParams
): Promise<ApiResponse<OauthLoginResult>> {
  if (USE_REAL_API) {
    return request.post('/auth/oauth/callback', params)
  }

  return {
    code: 20000,
    message: 'success',
    data: buildMockOauthUser(params.redirectPath)
  }
}

export async function logoutApi(): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post('/auth/logout')
  }

  return {
    code: 20000,
    message: 'success',
    data: null
  }
}