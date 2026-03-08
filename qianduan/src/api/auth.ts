import request from './request'
import { USE_REAL_API } from '../config/env'
import type { UserRole } from '../stores/auth'

// 后面接真实后端时，就在这里改
// 现在先用 Promise 模拟接口返回

export interface LocalLoginParams {
  username: string
  password: string
}

export interface LocalLoginResult {
  token: string
  user: {
    username: string
    role: string
  }
}

export interface OauthCallbackParams {
  providerId: string
  code: string
  state: string
  redirectPath: string
}

export interface OauthLoginResult {
  token: string
  role: UserRole
  username: string
  realName: string
  userId: number
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export function localLogin(params: LocalLoginParams): Promise<LocalLoginResult> {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (params.username === 'admin' && params.password === '123456') {
        resolve({
          token: 'admin-token-123456',
          user: {
            username: 'admin',
            role: 'ROLE3'
          }
        })
      } else {
        reject(new Error('用户名或密码错误'))
      }
    }, 500)
  })
}

function buildMockOauthUser(redirectPath: string): OauthLoginResult {
  if (redirectPath.startsWith('/admin')) {
    return {
      token: 'mock-oauth-token-role3',
      role: 'ROLE3',
      username: 'oauth_admin',
      realName: '第三方管理员',
      userId: 1
    }
  }

  return {
    token: 'mock-oauth-token-role1',
    role: 'ROLE1',
    username: 'oauth_user',
    realName: '第三方用户',
    userId: 4
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
