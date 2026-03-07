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