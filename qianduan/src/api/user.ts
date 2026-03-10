import request from './request'
import { USE_REAL_API } from '../config/env'
import { useUserStore } from '../stores/user'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export type UserRole = 'ROLE1' | 'ROLE2' | 'ROLE3'
export type UserStatus = 'ENABLED' | 'DISABLED'

export interface UserItemResult {
  id: number
  username: string
  realName: string
  remark: string
  role: UserRole
  status: UserStatus
  createdAt: string
  localAccount: boolean
}

export interface CreateUserParams {
  username: string
  realName: string
  remark: string
  role: UserRole
  status: UserStatus
  initialPassword: string
}

export interface UpdateUserParams {
  id: number
  realName: string
  remark: string
  role: UserRole
  status: UserStatus
}

const USER_LIST_KEY = 'USER_LIST'
const USER_PASSWORD_MAP_KEY = 'USER_PASSWORD_MAP'

function getNowText() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
}

function readUserPasswordMap() {
  return JSON.parse(localStorage.getItem(USER_PASSWORD_MAP_KEY) || '{}') as Record<string, string>
}

function writeUserPasswordMap(map: Record<string, string>) {
  localStorage.setItem(USER_PASSWORD_MAP_KEY, JSON.stringify(map))
}

export async function getUserList(): Promise<ApiResponse<UserItemResult[]>> {
  if (USE_REAL_API) {
    return request.get('/users')
  }

  const userStore = useUserStore()

  return {
    code: 20000,
    message: 'success',
    data: userStore.userList
  }
}

export async function createUserApi(
  params: CreateUserParams
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post('/users', params)
  }

  const userStore = useUserStore()
  const userList = JSON.parse(localStorage.getItem(USER_LIST_KEY) || '[]') as UserItemResult[]

  const existed = userList.some(
    (item) =>
      String(item.username || '').toLowerCase() === params.username.toLowerCase()
  )

  if (existed) {
    return {
      code: 40001,
      message: '账号已存在',
      data: null
    }
  }

  const nextId =
    userList.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0) + 1

  const nextList: UserItemResult[] = [
    {
      id: nextId,
      username: params.username,
      realName: params.realName,
      remark: params.remark,
      role: params.role,
      status: params.status,
      createdAt: getNowText(),
      localAccount: true
    },
    ...userList
  ]

  localStorage.setItem(USER_LIST_KEY, JSON.stringify(nextList))

  const passwordMap = readUserPasswordMap()
  passwordMap[params.username] = params.initialPassword
  writeUserPasswordMap(passwordMap)

  userStore.refreshFromStorage()

  return {
    code: 20000,
    message: '创建成功',
    data: null
  }
}

export async function updateUserRole(
  id: number,
  role: UserRole
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/users/${id}/role`, { role })
  }

  void id
  void role

  return {
    code: 20000,
    message: '角色更新成功',
    data: null
  }
}

export async function updateUserStatus(
  id: number,
  status: UserStatus
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/users/${id}/status`, { status })
  }

  void id
  void status

  return {
    code: 20000,
    message: '状态更新成功',
    data: null
  }
}

export async function updateUserApi(
  params: UpdateUserParams
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.put(`/users/${params.id}`, params)
  }

  const userStore = useUserStore()
  const userList = JSON.parse(localStorage.getItem(USER_LIST_KEY) || '[]') as UserItemResult[]
  const exists = userList.some((item) => item.id === params.id)

  if (!exists) {
    return {
      code: 40404,
      message: '用户不存在',
      data: null
    }
  }

  const current = userList.find((item) => item.id === params.id)
  if (current && !current.localAccount && current.realName !== params.realName) {
    return {
      code: 40001,
      message: '第三方用户名称不可修改',
      data: null
    }
  }

  const nextList = userList.map((item) =>
    item.id === params.id
      ? {
          ...item,
          realName: params.realName,
          remark: params.remark,
          role: params.role,
          status: params.status
        }
      : item
  )

  localStorage.setItem(USER_LIST_KEY, JSON.stringify(nextList))
  userStore.refreshFromStorage()

  return {
    code: 20000,
    message: '更新成功',
    data: null
  }
}

export async function deleteUserApi(id: number): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.delete(`/users/${id}`)
  }

  const userStore = useUserStore()
  const target = userStore.userList.find((item) => item.id === id)

  if (!target) {
    return {
      code: 40404,
      message: '用户不存在',
      data: null
    }
  }

  const nextList = userStore.userList.filter((item) => item.id !== id)
  localStorage.setItem(USER_LIST_KEY, JSON.stringify(nextList))

  const passwordMap = readUserPasswordMap()
  delete passwordMap[target.username]
  writeUserPasswordMap(passwordMap)

  userStore.refreshFromStorage()

  return {
    code: 20000,
    message: '删除成功',
    data: null
  }
}

export async function resetUserPasswordApi(
  id: number,
  newPassword: string
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post(`/users/${id}/password/reset`, { newPassword })
  }

  const userStore = useUserStore()
  const target = userStore.userList.find((item) => item.id === id)
  if (!target) {
    return {
      code: 40404,
      message: '用户不存在',
      data: null
    }
  }

  if (!target.localAccount) {
    return {
      code: 40001,
      message: '仅本地用户支持重置密码',
      data: null
    }
  }

  const passwordMap = readUserPasswordMap()
  passwordMap[target.username] = newPassword
  writeUserPasswordMap(passwordMap)

  return {
    code: 20000,
    message: '重置成功',
    data: null
  }
}

export async function changeOwnPasswordApi(
  oldPassword: string,
  newPassword: string
): Promise<ApiResponse<null>> {
  if (USE_REAL_API) {
    return request.post('/users/password/change', { oldPassword, newPassword })
  }

  const username = localStorage.getItem('AUTH_USERNAME') || ''
  if (!username) {
    return {
      code: 40101,
      message: '未登录',
      data: null
    }
  }

  const userStore = useUserStore()
  const current = userStore.userList.find((item) => item.username === username)
  if (!current?.localAccount) {
    return {
      code: 40001,
      message: '当前账号不是本地账号',
      data: null
    }
  }

  const passwordMap = readUserPasswordMap()
  const saved = passwordMap[username] || '123456'
  if (saved !== oldPassword) {
    return {
      code: 40001,
      message: '旧密码错误',
      data: null
    }
  }

  passwordMap[username] = newPassword
  writeUserPasswordMap(passwordMap)

  return {
    code: 20000,
    message: '修改成功',
    data: null
  }
}
