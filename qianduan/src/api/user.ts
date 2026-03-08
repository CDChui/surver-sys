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
  role: UserRole
  status: UserStatus
  createdAt: string
}

export interface CreateUserParams {
  username: string
  realName: string
  role: UserRole
  status: UserStatus
}

export interface UpdateUserParams {
  id: number
  realName: string
  role: UserRole
  status: UserStatus
}

const USER_LIST_KEY = 'USER_LIST'

function getNowText() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${hh}:${mm}`
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
      message: '用户名已存在',
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
      role: params.role,
      status: params.status,
      createdAt: getNowText()
    },
    ...userList
  ]

  localStorage.setItem(USER_LIST_KEY, JSON.stringify(nextList))
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

  const nextList = userList.map((item) =>
    item.id === params.id
      ? {
          ...item,
          realName: params.realName,
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

  void id

  return {
    code: 20000,
    message: '删除成功',
    data: null
  }
}
